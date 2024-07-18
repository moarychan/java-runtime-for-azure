package com.microsoft.aspire.dcp;

import com.microsoft.aspire.AppHost;
import com.microsoft.aspire.DistributedApplication;
import com.microsoft.aspire.resources.Container;

import java.util.concurrent.ExecutionException;

public interface DcpAppHost extends AppHost {
    @Override
    default void run() {
        DcpOptions dcpOptions = new DcpOptions();
        dcpOptions.setBinPath("/Users/xiada/Documents/Projects/aspire4j/aspire4j/aspire4j-dcp/bin/darwin_arm64_0.5.7/ext/bin");
        dcpOptions.setCliPath("/Users/xiada/Documents/Projects/aspire4j/aspire4j/aspire4j-dcp/bin/darwin_arm64_0.5.7/dcp");
        dcpOptions.setExtensionsPath("/Users/xiada/Documents/Projects/aspire4j/aspire4j/aspire4j-dcp/bin/darwin_arm64_0.5.7/ext");

        DcpInfo dcpInfo = new DcpInfo();
        Locations locations = new Locations();

        // FIXME: this is just for test, remove it later
        DistributedApplication.getInstance()
                .addResource(new Container<>("for-test-redis").withImage("docker.io/library/redis:7.2"));

        DcpDependencyCheckServiceImpl dcpDependencyCheckService = new DcpDependencyCheckServiceImpl(dcpOptions);

        ApplicationExecutor applicationExecutor = new ApplicationExecutor(
                DistributedApplication.getInstance(),
                new KubernetesService(dcpOptions, locations),
                dcpOptions,
                dcpDependencyCheckService);

        DcpHostService dcpHostService = new DcpHostService(dcpOptions, applicationExecutor, dcpDependencyCheckService, locations);
        try {
            dcpHostService.startAsync().get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        // Add a shutdown hook to stop the service gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered");
            dcpHostService.stopAsync();
        }));

        // Simulate main application running
        System.out.println("Dcp host running...");
        while (true) {
            try {
                Thread.sleep(20000); // Simulate some work in the main thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }    
        }
        System.out.println("Dcp host finished...");
    }
}
