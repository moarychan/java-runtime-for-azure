package com.azure.runtime.host.extensions.microservice.common.resources;

import com.azure.runtime.host.DistributedApplication;
import com.azure.runtime.host.extensions.microservice.common.utils.BuildIntrospector;
import com.azure.runtime.host.extensions.microservice.common.utils.DeploymentStrategy;
import com.azure.runtime.host.resources.Container;
import com.azure.runtime.host.resources.DockerFile;
import com.azure.runtime.host.resources.ResourceType;
import com.azure.runtime.host.resources.traits.IntrospectiveResource;
import com.azure.runtime.host.resources.traits.ResourceWithReference;
import com.azure.runtime.host.resources.traits.ResourceWithTemplate;
import com.azure.runtime.host.utils.FileUtilities;
import com.azure.runtime.host.utils.json.RelativePath;
import com.azure.runtime.host.utils.templates.TemplateDescriptor;
import com.azure.runtime.host.utils.templates.TemplateDescriptorsBuilder;
import com.azure.runtime.host.utils.templates.TemplateEngine;
import com.azure.runtime.host.utils.templates.TemplateFileOutput;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MicroserviceProject<T extends MicroserviceProject<T>> extends Container<T>
        implements ResourceWithTemplate<T>, IntrospectiveResource, ResourceWithReference<T> {

    @JsonIgnore
    private static final ResourceType resourceType = ResourceType.fromString("project.spring.image.v0"); // FIXME

    @Valid
    @JsonProperty("strategies")
    private Set<DeploymentStrategy> strategies;
    
    @JsonIgnore
    private Map<String, String> introspectOutputEnvs = new HashMap<>();

    @NotNull(message = "MicroserviceProject.path cannot be null")
    @NotEmpty(message = "MicroserviceProject.path cannot be an empty string")
    @JsonProperty("path")
    @RelativePath
    private String path;

    @JsonIgnore
    private boolean openTelemetryEnabled;
    
    private String templateDockerfilePath;
    
    protected MicroserviceProject(ResourceType type, String name) {
        super(type, name, null);

        // establish a convention that a Microservice Project will have a path that is equal to the name of the project
        withPath(name);
    }

    private MicroserviceProject(String name, ResourceType type) {
        super(type, name, null);
    }

    @Override
    public void onResourcePrecommit() {
        super.onResourcePrecommit();
        introspect();
    }

    @Override
    public void introspect() {
        this.introspectOutputEnvs = new HashMap<>();
        // we add the available strategies to the JavaAppHostManifest and leave it to azd to try its best...
        this.strategies = new BuildIntrospector().introspect(this, this.introspectOutputEnvs);

        // Add the environment introspected from the project
        introspectOutputEnvs.forEach((k, v) -> {
            if (!k.startsWith("BUILD_")) {
                withEnvironment(k, v);
            }
        });
        if (introspectOutputEnvs.containsKey("SERVER_PORT")) {
            int serverPort = Integer.parseInt(introspectOutputEnvs.get("SERVER_PORT"));
            withHttpEndpoint(serverPort);
        }

        if (introspectOutputEnvs.containsKey("BUILD_IMAGE")) {
            withImage(introspectOutputEnvs.get("BUILD_IMAGE"));
        }
    }
    
    private void substituteResources(Map<String, String> outputEnvs) {
        // but, we also look in the strategies to see if we found a dockerfile strategy, as in that case we transform
        // this entire output from a MicroserviceProject resource into a dockerfile resource
        strategies.stream()
                .filter(s -> s.getType() == DeploymentStrategy.DeploymentType.DOCKER_FILE)
                .findFirst().ifPresent(s -> {
                    // we need to set the service name (to the existing project name), the path to the Dockerfile, and the
                    // context name (which is the directory containing the Dockerfile)
                    // FIXME ugly generics
                    DockerFile<?> dockerFile = new DockerFile<>(getName());

                    String dockerFilePath = s.getCommands().get(0)[0];
                    String contextPath = Paths.get(dockerFilePath).getParent().toString();
                    this.copyInto(dockerFile);
                    dockerFile.withPath(dockerFilePath)
                            .withContext(contextPath)
                            .withExternalHttpEndpoints(); // FIXME this is not really the context

                    DistributedApplication.getInstance().substituteResource(this, dockerFile);
                });

        // if we need to rebuild the image with more attributes
        if ("true".equals(outputEnvs.get("BUILD_ADD_OTEL_AGENT"))) {
            strategies.stream()
                    .filter(s -> s.getType() == DeploymentStrategy.DeploymentType.MAVEN_POM)
                    .findFirst().ifPresent(s -> {
                                String imageResourceName = getName() + "-image";
                                MicroserviceProject<?> containerImage = new MicroserviceProject<>(imageResourceName, resourceType);
                                containerImage.strategies = strategies;
                                containerImage.withImage(outputEnvs.get("BUILD_IMAGE"))
                                        .withPath(this.getPath());
                                DistributedApplication.getInstance().addResource(containerImage);

                                DockerFile<?> dockerFile = new DockerFile<>(getName());
                                String dockerFilePath = this.templateDockerfilePath;
                                String contextPath = Paths.get(dockerFilePath).getParent().toString();
                                this.copyInto(dockerFile);
                                dockerFile.withPath(dockerFilePath)
                                        .withContext(contextPath)
                                        .withExternalHttpEndpoints()
                                        .withBuildArg("BASE_IMAGE", "${%s.image}".formatted(imageResourceName))
                                ; // FIXME this is not really the context

                                DistributedApplication.getInstance().substituteResource(this, dockerFile);
                            }
                    );
        }
    }

    /**
     * The path to the project file. Relative paths are interpreted as being relative to the location of the manifest file.
     * @param path
     * @return
     */
    @JsonIgnore
    public T withPath(String path) {
        this.path = path;
        return self();
    }

    @JsonIgnore
    public final String getPath() {
        return path;
    }

    @JsonIgnore
    public T withOpenTelemetry() {
        this.openTelemetryEnabled = true;
        return self();
    }

    @JsonIgnore
    public boolean isOpenTelemetryEnabled() {
        return openTelemetryEnabled;
    }

    @Override
    public T self() {
        return (T)this;
    }

    @Override
    public List<TemplateFileOutput> processTemplate() {
        if (openTelemetryEnabled) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("BASE_IMAGE", "${BASE_IMAGE}");
            final String templatePath = "/templates/opentelemetry/";
            final String outputRootPath = "opentelemetry/";
            List<TemplateDescriptor> templateFiles = TemplateDescriptorsBuilder.begin(templatePath, outputRootPath)
                    .with("Dockerfile")
                    .with("JAVA_TOOL_OPTIONS.append")
                    .with("JAVA_TOOL_OPTIONS.delim")
                    .build();

            List<TemplateFileOutput> templateOutput = TemplateEngine.getTemplateEngine()
                .process(MicroserviceProject.class, templateFiles, properties);

            // Important - as noted in the javadoc - from the perspective of the API below, the paths are relative to the
            // directory in which azd is running, NOT the output directory. These paths will then be transformed at
            // serialization time to be relative to the output directory.
            // This is slightly unfortunate, as we know the correct directory here, but we don't have a way to pass it.
            templateDockerfilePath = FileUtilities.convertOutputPathToRootRelative(outputRootPath + "Dockerfile").toString();
            
            return templateOutput;
        }
        return List.of();
    }

    protected Map<String, String> getIntrospectOutputEnvs() {
        return introspectOutputEnvs;
    }
}
