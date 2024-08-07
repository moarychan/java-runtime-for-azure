package com.azure.runtime.host.extensions.spring.resources;

import com.azure.runtime.host.extensions.microservice.common.resources.MicroserviceProject;
import com.azure.runtime.host.resources.Resource;
import com.azure.runtime.host.resources.ResourceType;
import com.azure.runtime.host.resources.traits.IntrospectiveResource;
import com.azure.runtime.host.resources.traits.ResourceWithTemplate;

import java.util.ArrayList;
import java.util.List;

public class SpringProject extends MicroserviceProject<SpringProject>
                            implements ResourceWithTemplate<SpringProject>, IntrospectiveResource {    
    private static final ResourceType SPRING_PROJECT = ResourceType.fromString("project.spring.v0");

    private List<Resource<?>> dependencies; 
    
    public SpringProject(String name) {
        this(SPRING_PROJECT, name);
        this.dependencies = new ArrayList<>();
    }

    private SpringProject(ResourceType type, String name) {
        super(type, name);
//        withEnvironment("spring.application.name", name);
    }
    
    public SpringProject withDependency(Resource<?> resource) {
        this.dependencies.add(new DependencyResource(resource.getType(), resource.getName()));
        return self();
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
