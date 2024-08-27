import com.azure.runtime.host.Extension;
import com.azure.runtime.host.extensions.azure.keyvault.AzureKeyVaultExtension;

/**
 * An extension providing support for Azure Cosmos Mongo.
 */
module com.azure.runtime.host.extensions.azure.keyvault {
    requires transitive com.azure.runtime.host;

    exports com.azure.runtime.host.extensions.azure.keyvault;
    exports com.azure.runtime.host.extensions.azure.keyvault.resources;

    opens com.azure.runtime.host.extensions.azure.keyvault to org.hibernate.validator, com.fasterxml.jackson.databind;
    opens com.azure.runtime.host.extensions.azure.keyvault.resources to com.fasterxml.jackson.databind, org.hibernate.validator;

    // We conditionally open up the template files to the apphost, so it can write them out
    opens templates.bicep to com.azure.runtime.host;

    provides Extension with AzureKeyVaultExtension;
}
