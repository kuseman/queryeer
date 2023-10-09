package se.kuseman.payloadbuilder.catalog.jdbc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Constants for Jdbc module */
public interface Constants
{
    public static final String TITLE = "Jdbc";
    public static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

}
