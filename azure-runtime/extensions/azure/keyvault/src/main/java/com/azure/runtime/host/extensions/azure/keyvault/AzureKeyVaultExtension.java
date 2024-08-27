package com.azure.runtime.host.extensions.azure.keyvault;

import com.azure.runtime.host.DistributedApplication;
import com.azure.runtime.host.Extension;
import com.azure.runtime.host.extensions.azure.keyvault.resources.AzureKeyVaultResource;

public class AzureKeyVaultExtension implements Extension {

    @Override
    public String getName() {
        return "Azure Key Vault";
    }

    @Override
    public String getDescription() {
        return "Provides resources for Azure Key Vault.";
    }

    public AzureKeyVaultResource addAzureKeyVault(String name) {
        return DistributedApplication.getInstance().addResource(new AzureKeyVaultResource(name));
    }
}
