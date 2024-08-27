package com.azure.runtime.host.extensions.azure.keyvault.resources;

import com.azure.runtime.host.resources.AzureBicepResource;
import com.azure.runtime.host.resources.ResourceType;
import com.azure.runtime.host.resources.traits.ManifestExpressionProvider;
import com.azure.runtime.host.resources.traits.ResourceWithEndpoints;
import com.azure.runtime.host.resources.traits.ResourceWithEnvironment;
import com.azure.runtime.host.resources.traits.ResourceWithReference;
import com.azure.runtime.host.utils.templates.TemplateDescriptor;
import com.azure.runtime.host.utils.templates.TemplateDescriptorsBuilder;
import com.azure.runtime.host.utils.templates.TemplateEngine;
import com.azure.runtime.host.utils.templates.TemplateFileOutput;

import java.util.List;
import java.util.Map;

public class AzureKeyVaultResource extends AzureBicepResource<AzureKeyVaultResource>
    implements
      ResourceWithEndpoints<AzureKeyVaultResource>,
      ResourceWithReference<AzureKeyVaultResource>,
      ResourceWithEnvironment<AzureKeyVaultResource>,
      ManifestExpressionProvider {
    private static final ResourceType AZURE_KEY_VAULT = ResourceType.fromString("azure.bicep.v0");

    public AzureKeyVaultResource(String name) {
        super(AZURE_KEY_VAULT, name);
        withParameter("principalId", "");
    }

    @Override
    public String getValueExpression() {
        return "{" + getName() + ".outputs.endpoint}";
    }

    public AzureKeyVaultResource withSecretKey(String secretKey) {
        withParameter("secretKey", secretKey);
        return super.self();
    }

    public AzureKeyVaultResource withSecretValue(String secretValue) {
        withParameter("secretValue", secretValue);
        return super.self();
    }

    @Override
    public List<TemplateFileOutput> processTemplate() {
        final String templatePath = "/templates/bicep/";
        List<TemplateDescriptor> templateFiles = TemplateDescriptorsBuilder.begin(templatePath)
            .with("keyvault.module.bicep", "${name}.keyvault.module.bicep")
            .build();

        List<TemplateFileOutput> templateOutput = TemplateEngine.getTemplateEngine()
            .process(AzureKeyVaultResource.class, templateFiles,
                Map.of(
                    "name", getName()
                ));

        // we know that we need to get the output filename from the first element, and set that as the path
        // FIXME we need a better way of determining the output path of the template
        withPath(templateOutput.get(0).filename());

        return templateOutput;
    }
}
