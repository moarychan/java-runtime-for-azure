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

public final class PrometheusService extends DockerFile<PrometheusService>
    implements ResourceWithTemplate<PrometheusService>,
        ResourceWithEndpoints<PrometheusService>,
        ResourceWithReference<PrometheusService> {

    private final String PROPERTY_NAME = "name";
    private final String PROPERTY_TARGET_PORT = "targetPort";

    private static final int DEFAULT_TARGET_PORT = 9091;

    private final Map<String, Object> properties;

    public PrometheusService(String name) {
        super(name);

        this.properties = new HashMap<>();
        this.properties.put(PROPERTY_NAME, name);
        this.properties.put(PROPERTY_TARGET_PORT, DEFAULT_TARGET_PORT);

        withPort(DEFAULT_TARGET_PORT);
    }

    public PrometheusService withPort(int targetPort) {
        this.properties.put(PROPERTY_TARGET_PORT, targetPort);

        withHttpEndpoint(targetPort);
        withHttpsEndpoint(targetPort);

        return this;
    }
    
    @Override
    public List<TemplateFileOutput> processTemplate() {
        final String templatePath = "/templates/prometheus/";
        final String outputRootPath = "prometheus/";
        List<TemplateDescriptor> templateFiles = TemplateDescriptorsBuilder.begin(templatePath, outputRootPath)
            .with("Dockerfile")
            .with("prometheus.yml")
            .build();

        List<TemplateFileOutput> templateOutput = TemplateEngine.getTemplateEngine()
            .process(PrometheusService.class, templateFiles, properties);

        // Important - as noted in the javadoc - from the perspective of the API below, the paths are relative to the
        // directory in which azd is running, NOT the output directory. These paths will then be transformed at
        // serialization time to be relative to the output directory.
        // This is slightly unfortunate, as we know the correct directory here, but we don't have a way to pass it.
        withPath(FileUtilities.convertOutputPathToRootRelative(outputRootPath + "Dockerfile").toString());
        withContext(FileUtilities.convertOutputPathToRootRelative(outputRootPath).toString());

        return templateOutput;
    }

    @Override
    public PrometheusService self() {
        return this;
    }
}
