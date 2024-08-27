package com.azure.runtime.host.extensions.azure.cosmos.mongo;

import com.azure.runtime.host.DistributedApplication;
import com.azure.runtime.host.Extension;
import com.azure.runtime.host.extensions.azure.cosmos.mongo.resources.AzureCosmosMongoResource;

public class AzureCosmosMongoExtension implements Extension {

    @Override
    public String getName() {
        return "Azure Cosmos Mongo";
    }

    @Override
    public String getDescription() {
        return "Provides resources for Azure Cosmos Mongo.";
    }

    public AzureCosmosMongoResource addAzureCosmosMongo(String name) {
        return DistributedApplication.getInstance().addResource(new AzureCosmosMongoResource(name));
    }
}
