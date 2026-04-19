package se.kuseman.payloadbuilder.catalog.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Test of {@link KafkaConnection}. */
class KafkaConnectionTest
{
    @Test
    void test_jaas_config_assembly()
    {
        KafkaConnection connection = new KafkaConnection();
        connection.setSecurityProtocol(KafkaConnection.SecurityProtocol.SASL_SSL);
        connection.setSaslMechanism(KafkaConnection.SaslMechanism.SCRAM_SHA_512);
        connection.setSaslJaasLoginModule("org.apache.kafka.common.security.scram.ScramLoginModule");
        connection.setSaslJaasControlFlag("required");
        connection.setSaslJaasUsername("user");
        connection.setRuntimeSaslJaasPassword("pa\"ss\\word".toCharArray());
        connection.setSaslJaasOptions("serviceName=\"kafka\"");

        assertEquals("org.apache.kafka.common.security.scram.ScramLoginModule required username=\"user\" password=\"pa\\\"ss\\\\word\" serviceName=\"kafka\";", connection.getSaslJaasConfig());
    }

    @Test
    void test_jaas_config_null_when_not_sasl()
    {
        KafkaConnection connection = new KafkaConnection();
        connection.setSecurityProtocol(KafkaConnection.SecurityProtocol.PLAINTEXT);
        connection.setSaslJaasUsername("user");
        connection.setRuntimeSaslJaasPassword("password".toCharArray());

        assertNull(connection.getSaslJaasConfig());
    }
}
