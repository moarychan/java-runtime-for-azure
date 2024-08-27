package com.azure.runtime.host.extensions.azure.cosmos.mongo.resources;

import com.azure.runtime.host.resources.AzureBicepResource;
import com.azure.runtime.host.resources.ResourceType;
import com.azure.runtime.host.resources.references.ReferenceExpression;
import com.azure.runtime.host.resources.traits.ResourceWithConnectionString;
import com.azure.runtime.host.resources.traits.ResourceWithEndpoints;
import com.azure.runtime.host.utils.templates.TemplateDescriptor;
import com.azure.runtime.host.utils.templates.TemplateDescriptorsBuilder;
import com.azure.runtime.host.utils.templates.TemplateEngine;
import com.azure.runtime.host.utils.templates.TemplateFileOutput;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

public class AzureCosmosMongoResource extends AzureBicepResource<AzureCosmosMongoResource>
    implements ResourceWithEndpoints<AzureCosmosMongoResource>, ResourceWithConnectionString<AzureCosmosMongoResource> {
    private static final ResourceType AZURE_COSMOS = ResourceType.fromString("azure.bicep.v0");

    public AzureCosmosMongoResource(String name) {
        super(AZURE_COSMOS, name);
    }

    @Override
    public ReferenceExpression getConnectionStringExpression() {
        return ReferenceExpression.create("{" + getName() + ".outputs.connectionString}");
    }

    @JsonIgnore
    @Override
    public String getValueExpression() {
        return "{" + getName() + ".outputs.connectionString}";
    }

    public AzureCosmosMongoResource withDatabaseName(String databaseName) {
        withParameter("databaseName", databaseName);
        return super.self();
    }

    @Override
    public List<TemplateFileOutput> processTemplate() {
        final String templatePath = "/templates/bicep/";
        List<TemplateDescriptor> templateFiles = TemplateDescriptorsBuilder.begin(templatePath)
            .with("cosmos.mongo.module.bicep", "${name}.mongo.module.bicep")
            .build();

        List<TemplateFileOutput> templateOutput = TemplateEngine.getTemplateEngine()
            .process(AzureCosmosMongoResource.class, templateFiles,
                Map.of("name", getName()));

        // we know that we need to get the output filename from the first element, and set that as the path
        // FIXME we need a better way of determining the output path of the template
        withPath(templateOutput.get(0).filename());

        return templateOutput;
    }
}
