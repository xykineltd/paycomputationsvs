//package com.xykine.computation.config;
//
//import com.mongodb.ConnectionString;
//import com.mongodb.MongoClientSettings;
//import com.mongodb.client.MongoClient;
//import com.mongodb.client.MongoClients;
//import lombok.AllArgsConstructor;
//import org.bson.UuidRepresentation;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
//import org.springframework.lang.NonNull;
//
//import java.util.concurrent.TimeUnit;
//
//@Configuration
//@EnableMongoRepositories(basePackages = "com.xykine.computation")
//public class MongoConfig {
//
//    @Value("${spring.data.mongodb.database}")
//    private String databaseName;
//
//    @Value("${spring.data.mongodb.uri}")
//    private String mongoUri;
//
//    // Pool + Socket configs
//    @Value("${mongodb.pool.max-size:50}")
//    private int maxPoolSize;
//
//    @Value("${mongodb.pool.min-size:5}")
//    private int minPoolSize;
//
//    @Value("${mongodb.pool.max-wait-time-ms:30000}")
//    private int maxWaitTimeMs;
//
//    @Value("${mongodb.pool.max-connection-idle-ms:600000}")
//    private int maxIdleTimeMs;
//
//    @Value("${mongodb.socket.connect-timeout-ms:10000}")
//    private int connectTimeoutMs;
//
//    @Value("${mongodb.socket.read-timeout-ms:30000}")
//    private int readTimeoutMs;
//
//    @Value("${mongodb.heartbeat.frequency-ms:10000}")
//    private int heartbeatFreqMs;
//
//    @Value("${mongodb.heartbeat.min-frequency-ms:5000}")
//    private int minHeartbeatFreqMs;
//
//    @Bean
//    public MongoClient mongoClient() {
//        ConnectionString cs = new ConnectionString(mongoUri);
//
//        MongoClientSettings settings = MongoClientSettings.builder()
//                .applyConnectionString(cs)
//                .uuidRepresentation(UuidRepresentation.STANDARD)
//                .applyToSocketSettings(b -> b
//                        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
//                        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS))
//                .applyToConnectionPoolSettings(b -> b
//                        .maxSize(maxPoolSize)
//                        .minSize(minPoolSize)
//                        .maxWaitTime(maxWaitTimeMs, TimeUnit.MILLISECONDS)
//                        .maxConnectionIdleTime(maxIdleTimeMs, TimeUnit.MILLISECONDS))
//                .applyToServerSettings(b -> b
//                        .heartbeatFrequency(heartbeatFreqMs, TimeUnit.MILLISECONDS)
//                        .minHeartbeatFrequency(minHeartbeatFreqMs, TimeUnit.MILLISECONDS))
//                .build();
//
//        return MongoClients.create(settings);
//    }
//
//    @Bean
//    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
//        return new MongoTemplate(mongoClient, databaseName);
//    }
//}