package com.azure.example.storageexplorer;

import com.azure.runtime.host.DistributedApplication;
import com.azure.runtime.host.dcp.DcpAppHost;
import com.azure.runtime.host.extensions.azure.storage.AzureStorageExtension;
import com.azure.runtime.host.extensions.spring.SpringExtension;
import manifold.azure.cosmos.mongo.extensions.com.azure.runtime.host.DistributedApplication.AzureCosmosMongoDistributedApplication;
import manifold.azure.keyvault.extensions.com.azure.runtime.host.DistributedApplication.AzureKeyVaultDistributedApplication;

public class StorageExplorerAppHost implements DcpAppHost {

    @Override public void configureApplication(DistributedApplication app) {
        app.printExtensions();

        // Create Azure Storage resources...
        var blobStorage = app.withExtension(AzureStorageExtension.class)
            .addAzureStorage("storage")
            .addBlobs("storage-explorer-blobs");
        var mongo = AzureCosmosMongoDistributedApplication
            .addAzureCosmosMongo(app,"cosmos-mongo")
            .withDatabaseName("Todo");
        var keyVault = AzureKeyVaultDistributedApplication
            .addAzureKeyVault(app, "keyvault")
            .withReference(mongo)
            .withSecretKey("AZURE-COSMOS-CONNECTION-STRING2")
            .withSecretValue(mongo.getConnectionStringExpression().getValue());

        // Create Azure OpenAI resources...
//         var openAI = app.withExtension(AzureOpenAIExtension.class)
//             .addAzureOpenAI("openai")
//             .withDeployment(using("gpt-35-turbo", "gpt-35-turbo", "0613"));

        // Let's bring Spring in to the mix...
        var spring = app.withExtension(SpringExtension.class);

        // Sprinkle in some Spring Eureka service discovery, so our microservices don't need to know about each other
        var eurekaServiceDiscovery = spring.addEurekaServiceDiscovery("eureka");

        // add our first Spring Boot project - a date service that tells us the current date / time
        var dateService = spring.addSpringProject("date-service")
            .withReference(eurekaServiceDiscovery)
            .withExternalHttpEndpoints();

        // and storage explorer - a webapp to upload / download / view resources in a storage blob container
        var storageExplorer = spring.addSpringProject("storage-explorer")
            .withExternalHttpEndpoints()
            .withReference(blobStorage)
            .withReference(mongo).withEnvironment("AZURE_KEY_VAULT_ENDPOINT", keyVault.getValueExpression())
            .withReference(keyVault)
            .withReference(eurekaServiceDiscovery)
            .withOpenTelemetry();
//            .withReference(openAI);
    }

    public static void main(String[] args) {
        new StorageExplorerAppHost().boot(args);
    }
}
