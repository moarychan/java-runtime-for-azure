package com.microsoft.aspire;

import com.microsoft.aspire.resources.*;
import jakarta.validation.Valid;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The DistributedApplication class is the main entry point for creating and configuring distributed applications.
 * It provides a fluent API for adding resources to the application, and it also provides a way to load extensions
 * that can be used to extend the functionality of the application.
 *
 * @see AppHost
 * @see Extension
 */
public class DistributedApplication {
    private static DistributedApplication INSTANCE;

    @Valid
    final AspireManifest manifest;

    private final List<Extension> extensions = new ArrayList<>();

    DistributedApplication() {
        manifest = new AspireManifest();
        loadExtensions();

        // FIXME This is hacky
        INSTANCE = this;
    }

    public static DistributedApplication getInstance() {
        return INSTANCE;
    }

    private void loadExtensions() {
        ServiceLoader.load(Extension.class).forEach(extensions::add);
    }


    /***************************************************************************
     *
     * Resource
     *
     **************************************************************************/

    /**
     * Add a new resource to the distributed application.
     *
     * @param r
     * @param <T>
     * @return
     */
    public <T extends Resource<?>> T addResource(T r) {
        return manifest.addResource(r);
    }

    /**
     * Sometimes a resource, upon introspection, needs to change its type. Rather than allow for types to mutable,
     * we instead support substituting a resource with one or more new resources.
     *
     * @param oldResource The resource to remove.
     * @param newResources The resource(s) to add in the place of the old resource.
     */
    public void substituteResource(Resource<?> oldResource, Resource<?>... newResources) {
        manifest.substituteResource(oldResource, newResources);
    }


    /***************************************************************************
     *
     * DockerFile
     *
     **************************************************************************/

    /**
     * Add a new DockerFile to the distributed application.
     *
     * @param dockerFile
     * @return
     * @param <T>
     */
    public <T extends DockerFile<?>> T addDockerFile(T dockerFile) {
        return manifest.addResource(dockerFile);
    }

    /**
     * Add a new DockerFile to the distributed application.
     *
     * @param name
     * @param path
     * @param context
     * @return
     */
    public DockerFile<?> addDockerFile(String name, String path, String context) {
        return manifest.addResource(new DockerFile<>(name, path, context));
    }


    /***************************************************************************
     *
     * Container
     *
     **************************************************************************/

    /**
     * Add a new container to the distributed application.
     *
     * @param container
     * @return
     * @param <T>
     */
    public <T extends Container<?>> T addContainer(T container) {
        return manifest.addResource(container);
    }

    /**
     * Add a new container to the distributed application.
     *
     * @param name
     * @param image
     * @return
     */
    public Container<?> addContainer(String name, String image) {
        return manifest.addResource(new Container<>(name, image));
    }


    /***************************************************************************
     *
     * Executable
     *
     **************************************************************************/

    /**
     * Add a new executable to the distributed application.
     *
     * @param executable
     * @return
     * @param <T>
     */
    public <T extends Executable<?>> T addExecutable(T executable) {
        return manifest.addResource(executable);
    }

    /**
     * Add a new executable to the distributed application.
     *
     * @param name
     * @param command
     * @param workingDirectory
     * @param args
     * @return
     */
    public Executable<?> addExecutable(String name, String command, String workingDirectory, String... args) {
        return manifest.addResource(new Executable<>(name, command, workingDirectory).withArguments(args));
    }


    /***************************************************************************
     *
     * Value
     *
     **************************************************************************/

    /**
     * Add a new value to the distributed application.
     *
     * @param value
     * @return
     * @param <T>
     */
    public <T extends Value<?>> T addValue(T value) {
        return manifest.addResource(value);
    }

    /**
     * Add a new value to the distributed application.
     *
     * @param name
     * @param key
     * @param value
     * @return
     */
    public Value<?> addValue(String name, String key, String value) {
        return manifest.addResource(new Value<>(name, key, value));
    }

    /***************************************************************************
     *
     * Extensions
     *
     **************************************************************************/

    /**
     * Print the available extensions to System.out.
     */
    public void printExtensions() {
        printExtensions(System.out);
    }

    /**
     * Print the available extensions to the provided PrintStream.
     *
     * @param out
     */
    public void printExtensions(PrintStream out) {
        out.println("Available Aspire4J Extensions:");
        extensions.stream().sorted(Comparator.comparing(Extension::getName)).forEach(e -> {
            out.println("  - " + e.getName() + " (" + e.getClass().getSimpleName() + ".class): " + e.getDescription());
        });
    }

    /**
     * Loads the specified extension and makes an instance of it available for configuration, but it does not
     * add the extension to the distributed application. This will happen when the extension is configured.
     *
     * @param extension
     * @return
     * @param <T>
     */
    public <T extends Extension> T withExtension(Class<T> extension) {
        try {
            return extension.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create a new instance of the extension class", e);
        }
    }


    /***************************************************************************
     *
     * Public utility methods
     *
     **************************************************************************/


    /***************************************************************************
     *
     * Non-public API
     *
     **************************************************************************/

}