package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import java.util.List;

import se.kuseman.payloadbuilder.catalog.jdbc.monitor.IServerMonitorExtension;
import se.kuseman.payloadbuilder.catalog.jdbc.monitor.ServerMonitorSection;

/** SQL Server implementation of {@link IServerMonitorExtension} using DMV queries */
public class SqlServerMonitorExtension implements IServerMonitorExtension
{
    //@formatter:off
    private static final String ACTIVE_SESSIONS_QUERY =
        "SELECT" +
        "    s.session_id," +
        "    s.login_name," +
        "    s.host_name," +
        "    s.program_name," +
        "    s.status," +
        "    DB_NAME(s.database_id) AS database_name," +
        "    s.cpu_time," +
        "    s.memory_usage * 8 AS memory_kb," +
        "    s.total_elapsed_time / 1000 AS elapsed_ms," +
        "    s.logical_reads," +
        "    s.writes," +
        "    s.open_transaction_count," +
        "    r.command," +
        "    r.wait_type," +
        "    r.wait_time AS wait_time_ms," +
        "    r.blocking_session_id," +
        "    r.percent_complete," +
        "    SUBSTRING(t.text, (r.statement_start_offset / 2) + 1," +
        "        ((CASE r.statement_end_offset WHEN -1 THEN DATALENGTH(t.text)" +
        "          ELSE r.statement_end_offset END - r.statement_start_offset) / 2) + 1) AS current_sql" +
        " FROM sys.dm_exec_sessions s" +
        " LEFT JOIN sys.dm_exec_requests r ON s.session_id = r.session_id" +
        " OUTER APPLY sys.dm_exec_sql_text(r.sql_handle) t" +
        " WHERE s.is_user_process = 1" +
        " ORDER BY s.session_id";

    private static final String WAIT_STATS_QUERY =
        "SELECT TOP 30" +
        "    wait_type," +
        "    waiting_tasks_count," +
        "    wait_time_ms," +
        "    max_wait_time_ms," +
        "    signal_wait_time_ms," +
        "    wait_time_ms - signal_wait_time_ms AS resource_wait_time_ms," +
        "    CAST(100.0 * wait_time_ms / NULLIF(SUM(wait_time_ms) OVER (), 0) AS DECIMAL(5,2)) AS pct_total" +
        " FROM sys.dm_os_wait_stats" +
        " WHERE waiting_tasks_count > 0" +
        "   AND wait_type NOT IN (" +
        "       'SLEEP_TASK','SLEEP_SYSTEMTASK','SLEEP_DBSTARTUP','SLEEP_DBTARTUP','SLEEP_TEMPDBSTARTUP'," +
        "       'SLEEP_MASTERDBREADY','SLEEP_MASTERMDREADY','SLEEP_MASTERUPGRADED','SLEEP_MSDBSTARTUP'," +
        "       'WAITFOR','LAZYWRITER_SLEEP','BROKER_TO_FLUSH','BROKER_TASK_STOP','CLR_AUTO_EVENT'," +
        "       'DISPATCHER_QUEUE_SEMAPHORE','FT_IFTS_SCHEDULER_IDLE_WAIT','HADR_WORK_QUEUE'," +
        "       'ONDEMAND_TASK_QUEUE','REQUEST_FOR_DEADLOCK_SEARCH','RESOURCE_QUEUE','SERVER_IDLE_CHECK'," +
        "       'SNI_HTTP_ACCEPT','SP_SERVER_DIAGNOSTICS_SLEEP','SQLTRACE_BUFFER_FLUSH'," +
        "       'SQLTRACE_INCREMENTAL_FLUSH_SLEEP','SQLTRACE_WAIT_ENTRIES','WAIT_XTP_OFFLINE_CKPT_NEW_LOG'," +
        "       'XE_DISPATCHER_WAIT','XE_TIMER_EVENT','BROKER_EVENTHANDLER','CHECKPOINT_QUEUE'," +
        "       'DBMIRROR_EVENTS_QUEUE','BROKER_TRANSMITTER','SLEEP_TEMPDBSTARTUP','SLEEP_TASK'" +
        "   )" +
        " ORDER BY wait_time_ms DESC";

    private static final String ACTIVE_LOCKS_QUERY =
        "SELECT" +
        "    tl.resource_type," +
        "    DB_NAME(tl.resource_database_id) AS database_name," +
        "    CASE tl.resource_type" +
        "        WHEN 'OBJECT' THEN OBJECT_NAME(tl.resource_associated_entity_id, tl.resource_database_id)" +
        "        ELSE CAST(tl.resource_associated_entity_id AS NVARCHAR(256))" +
        "    END AS object_name," +
        "    tl.resource_description," +
        "    tl.request_mode," +
        "    tl.request_status," +
        "    tl.request_session_id," +
        "    s.login_name," +
        "    s.host_name," +
        "    s.program_name" +
        " FROM sys.dm_tran_locks tl" +
        " INNER JOIN sys.dm_exec_sessions s ON tl.request_session_id = s.session_id" +
        " WHERE s.is_user_process = 1" +
        " ORDER BY tl.request_session_id, tl.resource_type";

    private static final String BLOCKING_QUERY =
        "SELECT" +
        "    r.session_id AS blocked_session_id," +
        "    r.blocking_session_id," +
        "    r.wait_type," +
        "    r.wait_time AS wait_time_ms," +
        "    r.wait_resource," +
        "    DB_NAME(r.database_id) AS database_name," +
        "    bs.login_name AS blocking_login," +
        "    bs.host_name AS blocking_host," +
        "    bs.program_name AS blocking_program," +
        "    SUBSTRING(bt.text, (r.statement_start_offset / 2) + 1," +
        "        ((CASE r.statement_end_offset WHEN -1 THEN DATALENGTH(bt.text)" +
        "          ELSE r.statement_end_offset END - r.statement_start_offset) / 2) + 1) AS blocked_sql" +
        " FROM sys.dm_exec_requests r" +
        " INNER JOIN sys.dm_exec_sessions bs ON r.blocking_session_id = bs.session_id" +
        " OUTER APPLY sys.dm_exec_sql_text(r.sql_handle) bt" +
        " WHERE r.blocking_session_id > 0" +
        " ORDER BY r.blocking_session_id, r.session_id";

    private static final String FILE_IO_QUERY =
        "SELECT" +
        "    DB_NAME(vfs.database_id) AS database_name," +
        "    mf.name AS logical_name," +
        "    mf.type_desc AS file_type," +
        "    vfs.io_stall_read_ms," +
        "    vfs.num_of_reads," +
        "    CASE WHEN vfs.num_of_reads > 0 THEN vfs.io_stall_read_ms / vfs.num_of_reads ELSE 0 END AS avg_read_ms," +
        "    vfs.io_stall_write_ms," +
        "    vfs.num_of_writes," +
        "    CASE WHEN vfs.num_of_writes > 0 THEN vfs.io_stall_write_ms / vfs.num_of_writes ELSE 0 END AS avg_write_ms," +
        "    vfs.io_stall AS total_io_stall_ms," +
        "    vfs.size_on_disk_bytes / 1048576 AS size_mb" +
        " FROM sys.dm_io_virtual_file_stats(NULL, NULL) vfs" +
        " JOIN sys.master_files mf ON vfs.database_id = mf.database_id AND vfs.file_id = mf.file_id" +
        " ORDER BY vfs.io_stall DESC";

    private static final String MEMORY_QUERY =
        "SELECT" +
        "    type AS memory_clerk_type," +
        "    SUM(pages_kb) AS pages_kb," +
        "    SUM(pages_kb) / 1024 AS pages_mb" +
        " FROM sys.dm_os_memory_clerks" +
        " GROUP BY type" +
        " HAVING SUM(pages_kb) > 0" +
        " ORDER BY pages_kb DESC";
    //@formatter:on

    private final List<ServerMonitorSection> sections;

    public SqlServerMonitorExtension()
    {
        //@formatter:off
        sections = List.of(
            new ServerMonitorSection("Active Sessions",
                    "User sessions and their current requests (sys.dm_exec_sessions + sys.dm_exec_requests)",
                    ACTIVE_SESSIONS_QUERY),
            new ServerMonitorSection("Wait Stats",
                    "Cumulative wait statistics, top 30 by wait time (sys.dm_os_wait_stats)",
                    WAIT_STATS_QUERY),
            new ServerMonitorSection("Active Locks",
                    "Current lock holders from user sessions (sys.dm_tran_locks)",
                    ACTIVE_LOCKS_QUERY),
            new ServerMonitorSection("Blocking",
                    "Sessions blocked by other sessions (sys.dm_exec_requests where blocking_session_id > 0)",
                    BLOCKING_QUERY),
            new ServerMonitorSection("File I/O",
                    "I/O stall statistics per database file (sys.dm_io_virtual_file_stats)",
                    FILE_IO_QUERY),
            new ServerMonitorSection("Memory Clerks",
                    "Memory usage by clerk type (sys.dm_os_memory_clerks)",
                    MEMORY_QUERY)
        );
        //@formatter:on
    }

    @Override
    public List<ServerMonitorSection> getSections()
    {
        return sections;
    }
}
