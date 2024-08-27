
/**
 * An extension providing support for Azure Cosmos Mongo.
 */
module com.azure.runtime.host.extensions.manifold.azure.cosmos.mongo {
    requires transitive com.azure.runtime.host.extensions.azure.cosmos.mongo;
    requires manifold.rt;
    requires manifold.ext.rt;

    exports manifold.azure.cosmos.mongo.extensions.com.azure.runtime.host.DistributedApplication;
}
