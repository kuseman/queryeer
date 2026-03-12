package se.kuseman.payloadbuilder.catalog.jdbc.monitor;

/** A named monitoring section that represents one tab in the server monitor panel */
public record ServerMonitorSection(String name, String description, String query)
{
}
