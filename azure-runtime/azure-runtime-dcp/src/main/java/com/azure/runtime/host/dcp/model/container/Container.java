package com.azure.runtime.host.dcp.model.container;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.azure.runtime.host.dcp.model.groupversion.Dcp;
import com.azure.runtime.host.dcp.model.common.CustomResource;

/**
 * Represents a Kubernetes Container as a custom resource.
 */
public class Container extends CustomResource<ContainerSpec, ContainerStatus> {

    /**
     * Constructs a Container with a specific ContainerSpec.
     * @param spec the ContainerSpec to initialize this Container with.
     */
    @JsonCreator
    public Container(ContainerSpec spec) {
        super(spec);
    }
    
    public Container() {
        super();
    }

    /**
     * Factory method to create a new Container instance.
     * @param name the name of the container.
     * @param image the Docker image for the container.
     * @return a new Container instance.
     */
    public static Container create(String name, String image) {
        ContainerSpec spec = new ContainerSpec();
        spec.setImage(image);
        Container container = new Container(spec);
        container.getMetadata().setName(name);
        container.getMetadata().setNamespace("");
        return container;
    }

    /**
     * Checks if logs are available for the container based on its current state.
     * @return true if logs are available, otherwise false.
     */
    public boolean isLogsAvailable() {
        ContainerStatus status = getStatus();
        return status != null && (
            ContainerState.STARTING.getState().equals(status.getState()) ||
            ContainerState.BUILDING.getState().equals(status.getState()) ||
            ContainerState.RUNNING.getState().equals(status.getState()) ||
            ContainerState.PAUSED.getState().equals(status.getState()) ||
            ContainerState.STOPPING.getState().equals(status.getState()) ||
            ContainerState.EXITED.getState().equals(status.getState()) ||
            (ContainerState.FAILED_TO_START.getState().equals(status.getState()) && status.getContainerId() != null)
        );
    }

    @Override
    public String getApiVersion() {
        return Dcp.GROUP_VERSION.toString();
    }

    @Override
    public String getKind() {
        return Dcp.CONTAINER_KIND;
    }

    @Override
    public String toString() {
        return "Container{" +
            super.toString() +
            '}';
    }
}
