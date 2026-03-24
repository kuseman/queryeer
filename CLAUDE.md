# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Full build with tests and quality checks
./mvnw clean verify test

# Skip tests (faster iteration)
./mvnw clean package -DskipTests=true

# Release build
mvn clean package -DperformRelease=true -DskipTests=true -Dcheckstyle.skip

# Run a single test class
./mvnw test -pl queryeer-ide -Dtest=MyTestClass

# Apply code formatting (Spotless)
./mvnw spotless:apply
```

Code style is enforced by Spotless (Eclipse formatter via `eclipse-code-formatter.xml`) and Checkstyle. Run `spotless:apply` before committing if formatting errors occur.

## Architecture Overview

Queryeer is a modular, extensible Swing-based query IDE. The project is split into four Maven modules:

- **`queryeer-api`** — Public extension API published to Maven Central. Defines all extension point interfaces that plugins implement (`IQueryEngine`, `IOutputExtension`, `IAIAssistantProvider`, etc.) and core service interfaces.
- **`queryeer-ide`** — Main application. Implements the MVC core (`QueryeerModel`, `QueryeerView`, `QueryeerController`) plus the text editor, plugin wiring, and UI.
- **`queryeer-catalog`** — Built-in query engine implementations (JDBC, Elasticsearch, filesystem, HTTP). Packaged as a standalone plugin JAR; not published to Maven Central.
- **`queryeer-dist`** — Assembles the distributable ZIP (`queryeer-<version>-dist.zip`) with `bin/`, `lib/`, `plugins/`, etc.

### Plugin System

Plugins are discovered at runtime via ClassGraph scanning the classpath. Classes implementing extension interfaces or annotated with `@Inject` are wired via constructor injection. Each plugin runs in its own classloader.

Core services are registered in `Main.wire()` and injected into plugins:
- `IEventBus` — Pub/sub event bus for decoupling components
- `IConfig` — JSON-backed user configuration
- `ICryptoService` — Jasypt-based encryption for stored credentials
- `IActionRegistry`, `IDialogFactory`, `IIconFactory`, `ITemplateService`, etc.

### Extension Points (in `queryeer-api`)

| Interface | Purpose |
|---|---|
| `IQueryEngine` | Add a new query engine / data source |
| `IOutputExtension` | Add a result output type (table, text, file…) |
| `IOutputFormatExtension` | Add result export format (JSON, CSV…) |
| `IAIAssistantProvider` | Add an AI chat provider |
| `IConfigurable` | Add entries to the settings dialog |

### Key IDE Components

- **`TextEditor`** — RSyntaxTextArea-based editor with multi-caret support (`MultiCaretSupport`, `MultiCaretEditHandler`, `MultiCaretState`)
- **`QueryeerModel`** — Central state: open files, backup, file watching
- **`QueryeerController`** — Routes actions and events; coordinates model ↔ view
- **`QueryeerView`** — Main Swing frame and layout
- **`assistant/` package** — AI chat panel; providers for Anthropic Claude and Claude Code

### Threading

- All Swing operations must run on the Event Dispatch Thread (EDT). A `CheckThreadViolationRepaintManager` is active in dev mode.
- Query execution, file watching, and backups run on background threads.

## Java & Dependencies

- Java 17, Maven 3.x
- UI: Swing + FlatLaf 3.5.4 theming
- Editor: RSyntaxTextArea 3.4.0
- Charting/graphs: JFreeChart 1.5.5, JGraphX 3.4.1.3
- JSON: Jackson 2.16.1
- Markdown: CommonMark 0.24.0
- Templates: Freemarker 2.3.32
- Payloadbuilder (query engine core): 1.10.1-SNAPSHOT from Sonatype snapshots

## Module queryeer-catalog

- When working with AntlrDocumentParser and sub classes unit tests are crucial and should always be written

## ANTLR Parsing / Code Completion

Read the relevant architecture document before modifying any parsing or completion code:

- [ANTLR_PARSING.md](./queryeer-catalog/ANTLR_PARSING.md) — generic base-class architecture
  (`AntlrDocumentParser`, `CodeCompletionCore`, shared data types, dialect extension pattern)
- [ANTLR_SQLSERVER_PARSING.md](./queryeer-catalog/ANTLR_SQLSERVER_PARSING.md) — T-SQL dialect
  specifics (`SqlServerDocumentParser`, T-SQL design decisions, T-SQL failure signatures)

**MANDATORY — update the correct document whenever changes are made to:**

| Changed code | Update this document |
|---|---|
| `AntlrDocumentParser` | `ANTLR_PARSING.md` |
| `CodeCompletionCore` (c3 engine) | `ANTLR_PARSING.md` |
| `ITextEditorDocumentParser` (public API) | `ANTLR_PARSING.md` |
| `CompletionItem`, `CompletionResult`, `TokenOffset`, `TableAlias`, `JoinOnContext` | `ANTLR_PARSING.md` |
| `AntlrDocumentParserTestBase` | `ANTLR_PARSING.md` |
| `SqlServerDocumentParser` | `ANTLR_SQLSERVER_PARSING.md` |
| `SqlServerDocumentParserTest` | `ANTLR_SQLSERVER_PARSING.md` |
| `PrestoDocumentParser` / `PrestoDocumentParserTest` | create `ANTLR_PRESTO_PARSING.md` following the same pattern |

**MANDATORY when fixing a parsing/completion bug:** after the fix, extend the
`Diagnosing Completion Bugs` section of the appropriate document with anything learned during
diagnosis that would have made finding the root cause faster — new failure signatures,
non-obvious ANTLR behaviors, useful debug patterns, etc. The goal is that each fix leaves the
diagnostics section more useful than it was before.
