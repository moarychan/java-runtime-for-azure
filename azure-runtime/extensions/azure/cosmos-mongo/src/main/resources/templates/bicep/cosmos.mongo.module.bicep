targetScope = 'resourceGroup'

@description('Location for the Cosmos DB account.')
param location string = resourceGroup().location

@description('Specifies the MongoDB server version to use.')
@allowed([
  '7.0'
])
param serverVersion string = '7.0'

@description('The default consistency level of the Cosmos DB account.')
@allowed([
  'Eventual'
  'ConsistentPrefix'
  'Session'
  'BoundedStaleness'
  'Strong'
])
param defaultConsistencyLevel string = 'Session'

@description('Max stale requests. Required for BoundedStaleness. Valid ranges, Single Region: 10 to 2147483647. Multi Region: 100000 to 2147483647.')
@minValue(10)
@maxValue(2147483647)
param maxStalenessPrefix int = 100000

@description('Max lag time (seconds). Required for BoundedStaleness. Valid ranges, Single Region: 5 to 84600. Multi Region: 300 to 86400.')
@minValue(5)
@maxValue(86400)
param maxIntervalInSeconds int = 300

@description('The name for the Mongo DB database')
param databaseName string

var consistencyPolicy = {
  Eventual: {
    defaultConsistencyLevel: 'Eventual'
  }
  ConsistentPrefix: {
    defaultConsistencyLevel: 'ConsistentPrefix'
  }
  Session: {
    defaultConsistencyLevel: 'Session'
  }
  BoundedStaleness: {
    defaultConsistencyLevel: 'BoundedStaleness'
    maxStalenessPrefix: maxStalenessPrefix
    maxIntervalInSeconds: maxIntervalInSeconds
  }
  Strong: {
    defaultConsistencyLevel: 'Strong'
  }
}
var locations = [
  {
    locationName: location
    failoverPriority: 0
    isZoneRedundant: false
  }
]

resource cosmos 'Microsoft.DocumentDB/databaseAccounts@2024-05-15' = {
  name: toLower(take('cosmos-${r"${uniqueString(resourceGroup().id)"}}', 24))
  location: location
  kind: 'MongoDB'
  properties: {
    publicNetworkAccess: 'Enabled'
    minimalTlsVersion: 'Tls12'
    consistencyPolicy: consistencyPolicy[defaultConsistencyLevel]
    databaseAccountOfferType: 'Standard'
    locations: locations
    capabilities: [
      {
        name: 'EnableMongo'
      }
    ]
    enableAutomaticFailover: true
    apiProperties: {
      serverVersion: serverVersion
    }
    BackupPolicy: {
      type: 'Periodic'
      migrationState: {
      }
    }
  }
  tags: {
    'aspire-resource-name': 'cosmos-mongo'
  }
}

resource database 'Microsoft.DocumentDB/databaseAccounts/mongodbDatabases@2024-05-15' = {
  parent: cosmos
  name: databaseName
  properties: {
    resource: {
      id: databaseName
    }
  }
}

output connectionString string = cosmos.listConnectionStrings().connectionStrings[0].connectionString
output endpoint string = cosmos.properties.documentEndpoint
