package manifold.azure.keyvault.extensions.com.azure.runtime.host.DistributedApplication;

import com.azure.runtime.host.extensions.azure.keyvault.resources.AzureKeyVaultResource;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import com.azure.runtime.host.DistributedApplication;

@Extension
public class AzureKeyVaultDistributedApplication {

  public static AzureKeyVaultResource addAzureKeyVault(@This DistributedApplication app, String name) {
    return app.addResource(new AzureKeyVaultResource(name));
  }
}
