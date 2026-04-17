package se.kuseman.payloadbuilder.catalog.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Constants for Kafka module. */
interface KafkaConstants
{
    String TITLE = "Kafka";
    ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
