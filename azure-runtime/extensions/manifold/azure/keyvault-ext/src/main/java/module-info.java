
/**
 * An extension providing support for Azure Key Vault.
 */
module com.azure.runtime.host.extensions.manifold.azure.keyvault {
    requires transitive com.azure.runtime.host.extensions.azure.keyvault;
    requires manifold.rt;
    requires manifold.ext.rt;

    exports manifold.azure.keyvault.extensions.com.azure.runtime.host.DistributedApplication;
}
