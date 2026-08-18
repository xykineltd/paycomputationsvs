package com.xykine.computation.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.BsonBinary;
import org.bson.BsonBinarySubType;
import org.bson.UuidRepresentation;
import org.bson.types.Binary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoRepositories(basePackages = "com.xykine.computation")
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    // Pool + Socket configs
    @Value("${mongodb.pool.max-size:50}")
    private int maxPoolSize;

    @Value("${mongodb.pool.min-size:5}")
    private int minPoolSize;

    @Value("${mongodb.pool.max-wait-time-ms:30000}")
    private int maxWaitTimeMs;

    @Value("${mongodb.pool.max-connection-idle-ms:600000}")
    private int maxIdleTimeMs;

    @Value("${mongodb.socket.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${mongodb.socket.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Value("${mongodb.heartbeat.frequency-ms:10000}")
    private int heartbeatFreqMs;

    @Value("${mongodb.heartbeat.min-frequency-ms:5000}")
    private int minHeartbeatFreqMs;

    @Bean
    public MongoClient mongoClient() {
        ConnectionString cs = new ConnectionString(mongoUri);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(cs)
                // Existing payroll summary _ids are BSON Binary UUID subtype 4 (STANDARD).
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .applyToSocketSettings(b -> b
                        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS))
                .applyToConnectionPoolSettings(b -> b
                        .maxSize(maxPoolSize)
                        .minSize(minPoolSize)
                        .maxWaitTime(maxWaitTimeMs, TimeUnit.MILLISECONDS)
                        .maxConnectionIdleTime(maxIdleTimeMs, TimeUnit.MILLISECONDS))
                .applyToServerSettings(b -> b
                        .heartbeatFrequency(heartbeatFreqMs, TimeUnit.MILLISECONDS)
                        .minHeartbeatFrequency(minHeartbeatFreqMs, TimeUnit.MILLISECONDS))
                .build();

        return MongoClients.create(settings);
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new BinaryToUuidConverter(),
                new UuidToBinaryConverter()
        ));
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(
            MongoDatabaseFactory mongoDatabaseFactory,
            MongoMappingContext mongoMappingContext,
            MongoCustomConversions mongoCustomConversions) {
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(mongoDatabaseFactory), mongoMappingContext);
        converter.setCustomConversions(mongoCustomConversions);
        converter.setCodecRegistryProvider(mongoDatabaseFactory);
        return converter;
    }

    @Bean
    public MongoTemplate mongoTemplate(
            MongoDatabaseFactory mongoDatabaseFactory,
            MappingMongoConverter mappingMongoConverter) {
        return new MongoTemplate(mongoDatabaseFactory, mappingMongoConverter);
    }

    private static final class BinaryToUuidConverter implements Converter<Binary, UUID> {
        @Override
        public UUID convert(Binary source) {
            byte type = source.getType();
            BsonBinary bsonBinary = new BsonBinary(type, source.getData());
            if (type == BsonBinarySubType.UUID_STANDARD.getValue()) {
                return bsonBinary.asUuid(UuidRepresentation.STANDARD);
            }
            if (type == BsonBinarySubType.UUID_LEGACY.getValue()) {
                return bsonBinary.asUuid(UuidRepresentation.JAVA_LEGACY);
            }
            byte[] data = source.getData();
            if (data != null && data.length == 16) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                return new UUID(buffer.getLong(), buffer.getLong());
            }
            throw new IllegalArgumentException("Cannot convert BSON Binary subtype " + type + " to UUID");
        }
    }

    private static final class UuidToBinaryConverter implements Converter<UUID, Binary> {
        @Override
        public Binary convert(UUID source) {
            BsonBinary bsonBinary = new BsonBinary(source, UuidRepresentation.STANDARD);
            return new Binary(bsonBinary.getType(), bsonBinary.getData());
        }
    }
}