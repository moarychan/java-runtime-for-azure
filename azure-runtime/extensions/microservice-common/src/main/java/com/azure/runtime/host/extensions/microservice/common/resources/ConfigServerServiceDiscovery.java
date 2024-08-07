package com.azure.runtime.host.extensions.microservice.common.resources;

import com.azure.runtime.host.resources.DockerFile;
import com.azure.runtime.host.resources.traits.ResourceWithEndpoints;
import com.azure.runtime.host.resources.traits.ResourceWithReference;
import com.azure.runtime.host.resources.traits.ResourceWithTemplate;
import com.azure.runtime.host.utils.FileUtilities;
import com.azure.runtime.host.utils.templates.TemplateDescriptor;
import com.azure.runtime.host.utils.templates.TemplateDescriptorsBuilder;
import com.azure.runtime.host.utils.templates.TemplateEngine;
import com.azure.runtime.host.utils.templates.TemplateFileOutput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigServerServiceDiscovery extends DockerFile<ConfigServerServiceDiscovery>
      implements ResourceWithTemplate<ConfigServerServiceDiscovery>, ResourceWithEndpoints<ConfigServerServiceDiscovery>, ResourceWithReference<ConfigServerServiceDiscovery> {

    private final String PROPERTY_NAME = "name";
    private final String PROPERTY_TARGET_PORT = "targetPort";
    private final String PROPERTY_GIT_REPOSITORY_PATH = "gitRepositoryPath";

    private static final int DEFAULT_TARGET_PORT = 8888;

    private final Map<String, Object> properties;

    public ConfigServerServiceDiscovery(String name) {
        super(name);

        this.properties = new HashMap<>();
        this.properties.put(PROPERTY_NAME, name);
        this.properties.put(PROPERTY_TARGET_PORT, DEFAULT_TARGET_PORT);

        withPort(DEFAULT_TARGET_PORT);
    }

    public ConfigServerServiceDiscovery withPort(int targetPort) {
        this.properties.put(PROPERTY_TARGET_PORT, targetPort);

        withHttpEndpoint(targetPort);
        withHttpsEndpoint(targetPort);

        return this;
    }

    public ConfigServerServiceDiscovery withGitRepositoryPath(String gitRepositoryPath) {
        this.properties.put(PROPERTY_GIT_REPOSITORY_PATH, gitRepositoryPath);
        return this;
    }

    @Override
    public List<TemplateFileOutput> processTemplate() {
        if (!properties.containsKey(PROPERTY_GIT_REPOSITORY_PATH)
            || properties.get(PROPERTY_GIT_REPOSITORY_PATH) == null
            || properties.get(PROPERTY_GIT_REPOSITORY_PATH).toString().isBlank()) {
            throw new RuntimeException("Please configure property git repository path with method 'ConfigServerServiceDiscovery.withGitRepositoryPath'");
        }

        final String templatePath = "/templates/config-server/";
        final String outputRootPath = "config-server/";
        List<TemplateDescriptor> templateFiles = TemplateDescriptorsBuilder.begin(templatePath, outputRootPath)
            .with("pom.xml")
            .with("Dockerfile")
            .with("ConfigServerApplication.java", "src/main/java/com/azure/runtime/host/spring/config/server/ConfigServerApplication.java")
            .with("application.yaml", "src/main/resources/application.yaml")
            .with("static/index.html", "src/main/resources/static/index.html")
            .build();

        List<TemplateFileOutput> templateOutput = TemplateEngine.getTemplateEngine()
            .process(ConfigServerServiceDiscovery.class, templateFiles, properties);

        // Important - as noted in the javadoc - from the perspective of the API below, the paths are relative to the
        // directory in which azd is running, NOT the output directory. These paths will then be transformed at
        // serialization time to be relative to the output directory.
        // This is slightly unfortunate, as we know the correct directory here, but we don't have a way to pass it.
        withPath(FileUtilities.convertOutputPathToRootRelative(outputRootPath + "Dockerfile").toString());
        withContext(FileUtilities.convertOutputPathToRootRelative(outputRootPath).toString());

        return templateOutput;
    }

    @Override
    public ConfigServerServiceDiscovery self() {
        return this;
    }
}
