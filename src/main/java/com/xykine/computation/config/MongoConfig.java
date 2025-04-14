package com.xykine.computation.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.AllArgsConstructor;
import org.bson.UuidRepresentation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.lang.NonNull;


@Configuration
@AllArgsConstructor
@EnableMongoRepositories(basePackages = "com.xykine.computation")
public class MongoConfig extends AbstractMongoClientConfiguration {

    private MongoClient mongoClient;

    @Override
    @NonNull

    protected String getDatabaseName() {
        String tenantId = TenantContext.getTenantId();
        return tenantId + "_db"; // e.g., aced_db, client2_db
    }

    @Override
    public MongoClient mongoClient() {
        return mongoClient;
    }


    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), getDatabaseName());

    }

}