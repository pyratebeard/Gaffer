/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gaffer.graphql;

import gaffer.graphql.definitions.Constants;
import gaffer.graphql.definitions.DataTypeGQLBuilder;
import gaffer.graphql.definitions.EdgeTypeGQLBuilder;
import gaffer.graphql.definitions.EntityTypeGQLBuilder;
import gaffer.graphql.fetch.EdgeByArgDataFetcher;
import gaffer.graphql.fetch.EntityByArgDataFetcher;
import gaffer.graphql.fetch.VertexArgDataFetcher;
import gaffer.store.schema.Schema;
import gaffer.store.schema.SchemaEdgeDefinition;
import gaffer.store.schema.SchemaEntityDefinition;
import gaffer.store.schema.TypeDefinition;
import graphql.GraphQL;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

/**
 * Given a Gaffer Graph, generates a GraphQL schema
 */
public class GafferQLSchemaBuilder {
    private static final Logger LOGGER = Logger.getLogger(GafferQLSchemaBuilder.class);

    private Schema gafferSchema;

    /**
     * Constructor to prevent building.
     */
    public GafferQLSchemaBuilder() {

    }

    public GafferQLSchemaBuilder gafferSchema(final Schema gafferSchema) {
        this.gafferSchema = gafferSchema;
        return this;
    }

    public GraphQL build() throws GrafferQLException {
        if (null == this.gafferSchema) {
            throw new GrafferQLException("Gaffer Schema given to be null");
        }

        // Setup GraphQL Root Query
        final GraphQLObjectType.Builder queryTypeBuilder = newObject()
                .name("QueryType");

        // Prepare lists for entity/edge type builders, keyed by their name in Gaffer
        final Map<String, GraphQLObjectType> dataObjectTypes = new HashMap<>();

        // Register the data types
        for (final Map.Entry<String, TypeDefinition> t : gafferSchema.getTypes().entrySet()) {
            final GraphQLObjectType vertexType = new DataTypeGQLBuilder()
                    .name(t.getKey())
                    .gafferSchema(gafferSchema)
                    .typeDefinition(t.getValue())
                    .build();
            dataObjectTypes.put(t.getKey(), vertexType);
        }

        // Create entity type builders
        for (final Map.Entry<String, SchemaEntityDefinition> entry : gafferSchema.getEntities().entrySet()) {
            final GraphQLObjectType type = new EntityTypeGQLBuilder()
                    .schema(gafferSchema)
                    .dataObjectTypes(dataObjectTypes)
                    .name(entry.getKey())
                    .elementDefinition(entry.getValue())
                    .build();

            queryTypeBuilder
                    .field(newFieldDefinition()
                            .name(type.getName())
                            .type(new GraphQLList(type))
                            .argument(newArgument()
                                    .name(Constants.VERTEX)
                                    .type(GraphQLString)
                                    .build())
                            .dataFetcher(new EntityByArgDataFetcher(type.getName()))
                            .build())
                    .build();
        }

        // Create Edge Type Builders
        for (final Map.Entry<String, SchemaEdgeDefinition> entry : gafferSchema.getEdges().entrySet()) {
            final GraphQLObjectType type = new EdgeTypeGQLBuilder()
                    .schema(gafferSchema)
                    .dataObjectTypes(dataObjectTypes)
                    .name(entry.getKey())
                    .elementDefinition(entry.getValue())
                    .build();

            queryTypeBuilder
                    .field(newFieldDefinition()
                            .name(type.getName())
                            .type(new GraphQLList(type))
                            .argument(newArgument()
                                    .name(Constants.VERTEX)
                                    .type(GraphQLString)
                                    .build())
                            .argument(newArgument()
                                    .name(Constants.SOURCE)
                                    .type(GraphQLString)
                                    .build())
                            .argument(newArgument()
                                    .name(Constants.DESTINATION)
                                    .type(GraphQLString)
                                    .build())
                            .dataFetcher(new EdgeByArgDataFetcher(type.getName()))
                            .build())
                    .build();
        }

        // Register vertex types with query
        for (final GraphQLObjectType type : dataObjectTypes.values()) {
            queryTypeBuilder
                    .field(newFieldDefinition()
                            .name(type.getName())
                            .type(type)
                            .argument(newArgument()
                                    .name(Constants.VERTEX)
                                    .type(GraphQLString)
                                    .build())
                            .dataFetcher(new VertexArgDataFetcher(Constants.VERTEX))
                            .build())
                    .build();
        }

        // Setup GraphQL schema
        final GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryTypeBuilder.build())
                .build();

        return new GraphQL(schema);
    }
}
