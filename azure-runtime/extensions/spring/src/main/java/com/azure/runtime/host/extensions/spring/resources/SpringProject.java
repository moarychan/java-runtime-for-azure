package com.azure.runtime.host.extensions.spring.resources;

import com.azure.runtime.host.extensions.microservice.common.resources.MicroserviceProject;
import com.azure.runtime.host.resources.Resource;
import com.azure.runtime.host.resources.ResourceType;
import com.azure.runtime.host.resources.traits.IntrospectiveResource;
import com.azure.runtime.host.resources.traits.ResourceWithTemplate;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class SpringProject extends MicroserviceProject<SpringProject>
                            implements ResourceWithTemplate<SpringProject>, IntrospectiveResource {    
    private static final ResourceType SPRING_PROJECT = ResourceType.fromString("project.spring.v0");

    @JsonIgnore
    private List<Resource<?>> dependencies; 
    
    public SpringProject(String name) {
        this(SPRING_PROJECT, name);
        this.dependencies = new ArrayList<>();
    }

    private SpringProject(ResourceType type, String name) {
        super(type, name);
        withEnvironment("spring.application.name", name);
    }

    @Override
    public void introspect() {
        super.introspect();
        
        super.getIntrospectOutputEnvs().forEach((k, v) -> {
            if ("CONFIG_SERVER_CLIENT_ENABLED".equals(k)) {
                withEnvironment("CONFIG_SERVER_URL", "{config-server.bindings.https.url}");
            }
            if ("BUILD_EUREKA_CLIENT_ENABLED".equals(k)) {
                // https://github.com/spring-cloud/spring-cloud-netflix/issues/2541
                withEnvironment("eureka.client.serviceUrl.defaultZone", "{eureka.bindings.https.url}/eureka/");
                withEnvironment("EUREKA_INSTANCE_PREFERIPADDRESS", "true");
            }
            if ("BUILD_EXPORTER_ZIPKIN_ENABLED".equals(k)) {
                withEnvironment("MANAGEMENT_ZIPKIN_TRACING_ENDPOINT", "{zipkin-server.bindings.https.url}/api/v2/spans");
            }
        });
        
    }

    private SpringProject withDependency(Resource<?> resource) {
        this.dependencies.add(new DependencyResource(resource.getType(), resource.getName()));
        return self();
    }

    @Override
    public SpringProject withReference(Resource<?> resource) {
        return super.withReference(resource).withDependency(resource);
    }

    public List<Resource<?>> getDependencies() {
        return new ArrayList<>(dependencies);
    }
    
    static class DependencyResource extends Resource<DependencyResource> {
        public DependencyResource(ResourceType type, String name) {
            super(type, name);
        }

        @Override
        public DependencyResource self() {
            return this;
        }
        
        public String getDependencyName() {
            return getName();
        }
        
    }
}
