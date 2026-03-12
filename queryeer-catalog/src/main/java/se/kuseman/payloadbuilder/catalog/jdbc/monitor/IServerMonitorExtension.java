package se.kuseman.payloadbuilder.catalog.jdbc.monitor;

import java.util.List;

/** Extension point for dialects that support server-side monitoring (sessions, waits, locks, etc.) */
public interface IServerMonitorExtension
{
    /** Return the monitoring sections (tabs) provided by this extension */
    List<ServerMonitorSection> getSections();
}
