package com.azure.example.petclinic;

import com.azure.runtime.host.DistributedApplication;
import com.azure.runtime.host.dcp.DcpAppHost;
import com.azure.runtime.host.extensions.microservice.common.resources.ConfigServerServiceDiscovery;
import com.azure.runtime.host.extensions.microservice.common.resources.EurekaServiceDiscovery;
import com.azure.runtime.host.extensions.spring.SpringExtension;

public class PetClinicAppHost implements DcpAppHost {
    
    public static void main(String[] args) {
        new PetClinicAppHost().boot(args);
    }

    @Override
    public void configureApplication(DistributedApplication app) {
        app.printExtensions();
        SpringExtension spring = app.withExtension(SpringExtension.class);
        EurekaServiceDiscovery discoveryServer = spring
            .addEurekaServiceDiscovery("eureka");
        ConfigServerServiceDiscovery configServer = spring
            .addConfigServerServiceDiscovery("config-server")
            .withGitRepositoryPath("https://github.com/spring-petclinic/spring-petclinic-microservices-config")
            .withExternalHttpEndpoints();
//        SpringProject configServer = spring.addSpringProject("spring-petclinic-config-server")
//                .withExternalHttpEndpoints();

        spring.addSpringProject("spring-petclinic-customers-service")
                .withDependency(discoveryServer)
                .withDependency(configServer)
                .withReference(configServer)
                .withReference(discoveryServer);
        spring.addSpringProject("spring-petclinic-vets-service")
                .withDependency(discoveryServer)
                .withDependency(configServer)
                .withReference(configServer)
                .withReference(discoveryServer);
        spring.addSpringProject("spring-petclinic-visits-service")
                .withDependency(discoveryServer)
                .withDependency(configServer)
                .withReference(configServer)
                .withReference(discoveryServer);
        spring.addSpringProject("spring-petclinic-api-gateway")
                .withDependency(discoveryServer)
                .withDependency(configServer)
                .withReference(configServer)
                .withReference(discoveryServer);
        spring.addSpringProject("spring-petclinic-admin-server")
                .withDependency(discoveryServer)
                .withDependency(configServer)
                .withReference(configServer)
                .withReference(discoveryServer);
    }
}
