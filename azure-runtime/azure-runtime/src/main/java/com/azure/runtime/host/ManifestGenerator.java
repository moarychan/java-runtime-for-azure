package com.azure.runtime.host;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.azure.runtime.host.implementation.utils.json.RelativePathModule;
import com.azure.runtime.host.implementation.utils.json.CustomSerializerModifier;
import com.azure.runtime.host.resources.traits.ResourceWithLifecycle;
import com.azure.runtime.host.resources.traits.ResourceWithTemplate;
import com.azure.runtime.host.utils.FileUtilities;
import com.azure.runtime.host.utils.templates.TemplateFileOutput;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

// Not public API
class ManifestGenerator {

    private static final Logger LOGGER = Logger.getLogger(ManifestGenerator.class.getName());

    private Path outputPath;

    void generateManifest(AppHost appHost, Path outputPath) {
        this.outputPath = outputPath;

        File outputDir = outputPath.toFile();
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        FileUtilities.setOutputPath(outputPath);

        DistributedApplication app = new DistributedApplication();
        appHost.configureApplication(app);
        processTemplates(app, outputPath);

        writeManifestToFile(app);
    }

    private void processTemplates(DistributedApplication app, Path outputPath) {
        LOGGER.info("Processing templates...");
        app.manifest.getResources().values().stream()
            .filter(r -> r instanceof ResourceWithTemplate<?>)
            .map(r -> (ResourceWithTemplate<?>) r)
            .map(ResourceWithTemplate::processTemplate)
            .forEach(templateFiles -> templateFiles.forEach(this::writeTemplateFile));
        LOGGER.info("Templates processed");
    }

    private ObjectMapper prepareObjectMapper(DistributedApplication app) {
        if (app.manifest.isEmpty()) {
            LOGGER.info("No configuration received from AppHost...exiting");
            System.exit(-1);
        }

        // run the precommit lifecycle hook on all resources
        callLifecyclePrecommitHook(app);

        LOGGER.info("Validating models...");
        // Firstly, disable the info logging messages that are printed by Hibernate Validator
        Logger.getLogger("org.hibernate.validator.internal.util.Version").setLevel(Level.OFF);

        // Get the logger for the Hibernate Validator class
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Set<ConstraintViolation<JavaAppHostManifest>> violations = validator.validate(app.manifest);
        if (!violations.isEmpty()) {
            for (ConstraintViolation<JavaAppHostManifest> violation : violations) {
                LOGGER.warning(violation.getMessage());
            }
            LOGGER.warning("Failed...exiting");
            System.exit(-1);
        } else {
            // object is valid, continue processing...
            LOGGER.info("Models validated...");
        }

        // Jackson ObjectMapper is used to serialize the JavaAppHostManifest object to a JSON string,
        // and write to a file named "aspire-manifest.json".
        ObjectMapper objectMapper = new ObjectMapper();

        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new CustomSerializerModifier());
        objectMapper.registerModule(module);
        objectMapper.registerModule(new RelativePathModule());

//        printAnnotations(System.out, app);

        return objectMapper;
    }

    // This is more-or-less for unit testing purposes
    String writeManifestToString(DistributedApplication app) {
        ObjectMapper objectMapper = prepareObjectMapper(app);
        LOGGER.info("Writing manifest to string");
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(app.manifest);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        } finally {
            LOGGER.info("Manifest written to string");
        }
    }

    void writeManifestToFile(DistributedApplication app) {
        ObjectMapper objectMapper = prepareObjectMapper(app);
        LOGGER.info("Writing manifest to file");
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(outputPath.toFile(), "aspire-manifest.json"), app.manifest);
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOGGER.info("Manifest written to file");
    }

    private void writeTemplateFile(TemplateFileOutput templateFile) {
        try {
            Path path = Paths.get(outputPath.toString() + "/" + templateFile.filename());

            // ensure the parent directories exist
            Files.createDirectories(path.getParent());
            Files.write(path, templateFile.content().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void callLifecyclePrecommitHook(DistributedApplication app) {
        Set<ResourceWithLifecycle> processedResources = new HashSet<>();
        Set<ResourceWithLifecycle> currentResources = new HashSet<>(app.manifest.getResources().values());

        while (!currentResources.isEmpty()) {
            // Create a snapshot of current resources to iterate over
            Set<ResourceWithLifecycle> snapshot = new HashSet<>(currentResources);
            for (ResourceWithLifecycle resource : snapshot) {
                if (!processedResources.contains(resource)) {
                    resource.onResourcePrecommit();
                    processedResources.add(resource);
                }
                currentResources.remove(resource);
            }
            // Update currentResources to include only new resources added during processing
            currentResources.addAll(app.manifest.getResources().values());
            currentResources.removeAll(processedResources);
        }
    }

    private void printAnnotations(PrintStream out, DistributedApplication app) {
        app.manifest.getResources().values().forEach(resource -> {
            out.println("Resource: " + resource.getName());
            resource.getAnnotations().forEach(annotation -> {
                out.println("  Annotation: " + annotation);
            });
        });
    }
}
