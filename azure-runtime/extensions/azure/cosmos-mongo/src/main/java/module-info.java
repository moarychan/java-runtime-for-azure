import com.azure.runtime.host.Extension;
import com.azure.runtime.host.extensions.azure.cosmos.mongo.AzureCosmosMongoExtension;

/**
 * An extension providing support for Azure Cosmos Mongo.
 */
module com.azure.runtime.host.extensions.azure.cosmos.mongo {
    requires transitive com.azure.runtime.host;

    exports com.azure.runtime.host.extensions.azure.cosmos.mongo;
    exports com.azure.runtime.host.extensions.azure.cosmos.mongo.resources;

    opens com.azure.runtime.host.extensions.azure.cosmos.mongo to org.hibernate.validator, com.fasterxml.jackson.databind;
    opens com.azure.runtime.host.extensions.azure.cosmos.mongo.resources to com.fasterxml.jackson.databind, org.hibernate.validator;

    // We conditionally open up the template files to the apphost, so it can write them out
    opens templates.bicep to com.azure.runtime.host;

    provides Extension with AzureCosmosMongoExtension;
}
