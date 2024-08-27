targetScope = 'resourceGroup'

@description('Location for the Key Vault.')
param location string = resourceGroup().location

@description('The key for other resource credential')
param secretKey string

@description('The value for other resource credential')
param secretValue string

@description('')
param principalId string

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: toLower(take('keyvault-${r"${uniqueString(resourceGroup().id)"}}', 24))
  location: location
  tags: {
    'aspire-resource-name': 'keyvault'
  }
  properties: {
    sku: {
      family: 'A'
      name: 'standard'
    }
    tenantId: subscription().tenantId
    accessPolicies: [
      {
        objectId: principalId
        permissions: {
          secrets: [
            'Get', 'List'
          ]
        }
        tenantId: subscription().tenantId
      }
    ]
  }
}

resource secrets 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: secretKey
  properties: {
    value: secretValue
  }
}

output endpoint string = keyVault.properties.vaultUri
