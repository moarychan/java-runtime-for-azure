package manifold.azure.cosmos.mongo.extensions.com.azure.runtime.host.DistributedApplication;

import com.azure.runtime.host.extensions.azure.cosmos.mongo.resources.AzureCosmosMongoResource;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import com.azure.runtime.host.DistributedApplication;

@Extension
public class AzureCosmosMongoDistributedApplication {

  public static AzureCosmosMongoResource addAzureCosmosMongo(@This DistributedApplication app, String name) {
    return app.addResource(new AzureCosmosMongoResource(name));
  }
}
