package se.kuseman.payloadbuilder.catalog.kafka;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Property;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;

/** Kafka connection definition. */
class KafkaConnection implements IPropertyAware
{
    @JsonProperty
    private String name;
    @JsonProperty
    private String bootstrapServers = "localhost:9092";
    @JsonProperty
    private String schemaRegistryUrl;
    @JsonProperty
    private SecurityProtocol securityProtocol = SecurityProtocol.PLAINTEXT;
    @JsonProperty
    private SaslMechanism saslMechanism = SaslMechanism.PLAIN;
    @JsonProperty
    private String saslJaasLoginModule = "org.apache.kafka.common.security.plain.PlainLoginModule";
    @JsonProperty
    private String saslJaasControlFlag = "required";
    @JsonProperty
    private String saslJaasUsername;
    /** Stored encrypted password if set. */
    @JsonProperty
    private String saslJaasPassword;
    @JsonProperty
    private String saslJaasOptions;
    @JsonProperty
    private boolean enabled = true;

    @JsonIgnore
    private List<String> topics;
    /** Runtime decrypted / prompted password. */
    @JsonIgnore
    private char[] runtimeSaslJaasPassword;

    KafkaConnection()
    {
    }

    KafkaConnection(KafkaConnection source)
    {
        this.name = source.name;
        this.bootstrapServers = source.bootstrapServers;
        this.schemaRegistryUrl = source.schemaRegistryUrl;
        this.securityProtocol = source.securityProtocol;
        this.saslMechanism = source.saslMechanism;
        this.saslJaasLoginModule = source.saslJaasLoginModule;
        this.saslJaasControlFlag = source.saslJaasControlFlag;
        this.saslJaasUsername = source.saslJaasUsername;
        this.saslJaasPassword = source.saslJaasPassword;
        this.saslJaasOptions = source.saslJaasOptions;
        this.enabled = source.enabled;
        this.topics = source.topics != null ? new ArrayList<>(source.topics)
                : null;
        this.runtimeSaslJaasPassword = source.runtimeSaslJaasPassword != null ? Arrays.copyOf(source.runtimeSaslJaasPassword, source.runtimeSaslJaasPassword.length)
                : null;
    }

    void setup(IQuerySession querySession, String catalogAlias)
    {
        querySession.setCatalogProperty(catalogAlias, KafkaCatalog.BOOTSTRAP_SERVERS, bootstrapServers);
        querySession.setCatalogProperty(catalogAlias, KafkaCatalog.SCHEMA_REGISTRY_URL, schemaRegistryUrl);
        querySession.setCatalogProperty(catalogAlias, KafkaCatalog.SECURITY_PROTOCOL, securityProtocol.name());
        querySession.setCatalogProperty(catalogAlias, KafkaCatalog.SASL_MECHANISM, saslMechanism.name());
        querySession.setCatalogProperty(catalogAlias, KafkaCatalog.SASL_JAAS_CONFIG, getSaslJaasConfig());
    }

    Properties toAdminClientProperties()
    {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("security.protocol", securityProtocol.name());

        if (isSaslEnabled())
        {
            props.put("sasl.mechanism", saslMechanism.name());
            String jaasConfig = getSaslJaasConfig();
            if (!isBlank(jaasConfig))
            {
                props.put("sasl.jaas.config", jaasConfig);
            }
        }
        return props;
    }

    boolean isSaslEnabled()
    {
        return securityProtocol == SecurityProtocol.SASL_PLAINTEXT
                || securityProtocol == SecurityProtocol.SASL_SSL;
    }

    boolean hasCredentials()
    {
        return !isSaslEnabled()
                || (!isBlank(saslJaasUsername)
                        && runtimeSaslJaasPassword != null
                        && runtimeSaslJaasPassword.length > 0);
    }

    String getSaslJaasConfig()
    {
        if (!isSaslEnabled())
        {
            return null;
        }

        String username = defaultIfBlank(saslJaasUsername, null);
        String password = runtimeSaslJaasPassword != null ? new String(runtimeSaslJaasPassword)
                : null;

        if (isBlank(saslJaasLoginModule)
                || isBlank(saslJaasControlFlag)
                || isBlank(username)
                || isBlank(password))
        {
            return null;
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append(saslJaasLoginModule)
                .append(' ')
                .append(saslJaasControlFlag)
                .append(' ')
                .append("username=\"")
                .append(escapeJaasValue(username))
                .append("\" ")
                .append("password=\"")
                .append(escapeJaasValue(password))
                .append('"');

        if (!isBlank(saslJaasOptions))
        {
            sb.append(' ')
                    .append(saslJaasOptions.trim());
        }
        if (!sb.toString()
                .endsWith(";"))
        {
            sb.append(';');
        }
        return sb.toString();
    }

    private static String escapeJaasValue(String value)
    {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    @Property(
            order = -1,
            title = "Name")
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Property(
            order = 1,
            title = "Bootstrap Servers",
            tooltip = "Comma separated values are supported")
    public String getBootstrapServers()
    {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers)
    {
        this.bootstrapServers = bootstrapServers;
    }

    @Property(
            order = 2,
            title = "Schema Registry URL",
            tooltip = "Comma separated values are supported")
    public String getSchemaRegistryUrl()
    {
        return schemaRegistryUrl;
    }

    public void setSchemaRegistryUrl(String schemaRegistryUrl)
    {
        this.schemaRegistryUrl = schemaRegistryUrl;
    }

    @Property(
            order = 3,
            title = "security_protocol")
    public SecurityProtocol getSecurityProtocol()
    {
        return securityProtocol;
    }

    public void setSecurityProtocol(SecurityProtocol securityProtocol)
    {
        this.securityProtocol = securityProtocol;
    }

    @Property(
            order = 4,
            title = "sasl_mechanism",
            visibleAware = true)
    public SaslMechanism getSaslMechanism()
    {
        return saslMechanism;
    }

    public void setSaslMechanism(SaslMechanism saslMechanism)
    {
        this.saslMechanism = saslMechanism;
    }

    @Property(
            order = 5,
            title = "sasl_jaas_login_module",
            visibleAware = true)
    public String getSaslJaasLoginModule()
    {
        return saslJaasLoginModule;
    }

    public void setSaslJaasLoginModule(String saslJaasLoginModule)
    {
        this.saslJaasLoginModule = saslJaasLoginModule;
    }

    @Property(
            order = 6,
            title = "sasl_jaas_control_flag",
            visibleAware = true)
    public String getSaslJaasControlFlag()
    {
        return saslJaasControlFlag;
    }

    public void setSaslJaasControlFlag(String saslJaasControlFlag)
    {
        this.saslJaasControlFlag = saslJaasControlFlag;
    }

    @Property(
            order = 7,
            title = "sasl_jaas_username",
            visibleAware = true)
    public String getSaslJaasUsername()
    {
        return saslJaasUsername;
    }

    public void setSaslJaasUsername(String saslJaasUsername)
    {
        this.saslJaasUsername = saslJaasUsername;
    }

    @Property(
            order = 8,
            title = "sasl_jaas_password",
            visibleAware = true,
            password = true)
    public String getSaslJaasPassword()
    {
        return saslJaasPassword;
    }

    public void setSaslJaasPassword(String saslJaasPassword)
    {
        this.saslJaasPassword = saslJaasPassword;
    }

    @Property(
            order = 9,
            title = "sasl_jaas_options",
            visibleAware = true,
            tooltip = "Optional extra JAAS options, e.g. serviceName=\"kafka\"")
    public String getSaslJaasOptions()
    {
        return saslJaasOptions;
    }

    public void setSaslJaasOptions(String saslJaasOptions)
    {
        this.saslJaasOptions = saslJaasOptions;
    }

    @Property(
            order = 10,
            title = "Enabled")
    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    List<String> getTopics()
    {
        return topics;
    }

    void setTopics(List<String> topics)
    {
        this.topics = topics;
    }

    char[] getRuntimeSaslJaasPassword()
    {
        return runtimeSaslJaasPassword;
    }

    void setRuntimeSaslJaasPassword(char[] runtimeSaslJaasPassword)
    {
        this.runtimeSaslJaasPassword = runtimeSaslJaasPassword;
    }

    @Override
    public boolean visible(String property)
    {
        if ("saslMechanism".equals(property)
                || "saslJaasLoginModule".equals(property)
                || "saslJaasControlFlag".equals(property)
                || "saslJaasUsername".equals(property)
                || "saslJaasPassword".equals(property)
                || "saslJaasOptions".equals(property))
        {
            return isSaslEnabled();
        }
        return true;
    }

    @Override
    public String toString()
    {
        return Objects.toString(name, bootstrapServers);
    }

    public enum SecurityProtocol
    {
        PLAINTEXT,
        SSL,
        SASL_PLAINTEXT,
        SASL_SSL
    }

    public enum SaslMechanism
    {
        PLAIN,
        SCRAM_SHA_256,
        SCRAM_SHA_512,
        GSSAPI,
        OAUTHBEARER
    }
}
