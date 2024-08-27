module storage.explorer.apphost {
    requires com.azure.runtime.host;

    requires com.azure.runtime.host.extensions.azure.openai;
    requires com.azure.runtime.host.extensions.azure.cosmos.mongo;
    requires com.azure.runtime.host.extensions.azure.keyvault;
    requires com.azure.runtime.host.extensions.azure.storage;
    requires com.azure.runtime.host.extensions.spring;
    requires com.azure.runtime.host.dcp;
    requires java.logging;
    requires com.azure.runtime.host.extensions.manifold.azure.cosmos.mongo;
    requires com.azure.runtime.host.extensions.manifold.azure.keyvault;
    requires com.azure.runtime.host.extensions.manifold.azure.storage;
}
