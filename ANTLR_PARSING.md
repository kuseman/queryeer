# ANTLR Parsing & Code Completion Architecture

> **IMPORTANT:** This document must be kept up to date whenever changes are made to
> `AntlrDocumentParser`, `SqlServerDocumentParser`, `PrestoDocumentParser`, or any class
> in the `dialect/` or `dialect/c3/` packages. It is loaded by AI agents to avoid
> expensive re-analysis of this complex code.

## Relevant Files

| File | Description |
|------|-------------|
| `queryeer-catalog/.../dialect/AntlrDocumentParser.java` | Abstract base — owns the full completion flow |
| `queryeer-catalog/.../dialect/SqlServerDocumentParser.java` | T-SQL dialect implementation |
| `queryeer-catalog/.../dialect/PrestoDocumentParser.java` | Presto SQL dialect implementation |
| `queryeer-catalog/.../dialect/c3/CodeCompletionCore.java` | Forked antlr4-c3 engine |
| `queryeer-api/.../editor/ITextEditorDocumentParser.java` | Public interface; defines `CompletionResult`, `CompletionItem` |
| `queryeer-catalog/.../test/.../AntlrDocumentParserTestBase.java` | Shared test suite; all dialect tests inherit this |
| `queryeer-catalog/.../test/.../SqlServerDocumentParserTest.java` | T-SQL specific tests |
| `queryeer-ide/.../editor/TextEditor.java` | Triggers `getCompletionItems(offset)` from the editor |

---

## Completion Trigger

`TextEditor` calls `parser.getCompletionItems(int offset)` passing the caret position. This
happens on DOT press and on the explicit completion shortcut. The method lives in
`AntlrDocumentParser`.

---

## High-Level Flow

```
getCompletionItems(offset)
  │
  ├─ findTokenFromOffset()          → TokenOffset (caret location + surrounding tokens)
  │
  └─ getCompletionItems(TokenOffset)
       │
       ├─ 1. getDialectSpecificCompletionItems()   ← hook: EXEC params, dotted prefix
       │      (returns early if non-null)
       │
       ├─ 2. detectJoinOnContext()                 ← hook: caret after "JOIN … ON"
       │      └─ if found → suggestFkJoinConditions()  (relevance: 10, returns early)
       │
       ├─ 3. isExpressionContext(node)?            ← hook
       │      └─ YES → suggestColumns()            ← hook
       │
       ├─ 4. isTableSourceContext(node)?           ← hook
       │      └─ YES → suggestTableSources()       ← hook
       │
       ├─ 5. getExpressionFallbackColumns()        ← hook: broken parse tree fallback
       │
       ├─ 6. Advance C3 token index past whitespace
       │
       └─ 7. C3 candidates → getC3CompletionItems() ← hook: dispatches grammar rules
                                                        to column/table/proc suggestions
```

After all of the above, clipboard IN-list completions are merged in unconditionally.

---

## Key Data Types

### `TokenOffset` (record)
Encapsulates position state passed through the entire completion flow:
- `caretOffset` — raw caret position in the document
- `suggestTokenIndex` — token stream index at the suggest position
- `tree` — parse tree node at the caret
- `prevTree` — previous terminal node (one token back)

`effectiveNode` is derived from these: if the caret is in whitespace/hidden channel, it falls
back to `prevTree` so the completion logic always works against a real token.

### `JoinOnContext` (record)
Returned by `detectJoinOnContext()` when the caret is directly after a `JOIN … ON` keyword:
- `rhsAlias` — the alias of the RHS table in the JOIN
- `allAliases` — all table aliases visible at this point

### `TableAlias` (record)
Represents a resolved table reference collected by `TableSourceAliasCollector`:
- `alias`, `objectName`, `extendedColumns`, `type` (`TableAliasType` enum)

`TableAliasType`: `TABLE`, `TEMPTABLE`, `TABLEVARIABLE`, `TABLE_FUNCTION`, `SUBQUERY`,
`CHANGETABLE`, `BUILTIN`

### `CompletionItem`
- `matchParts` — list of strings used for incremental matching (e.g. `["alias", "col"]`)
- `replacementText` — text inserted on accept
- `shortDesc` — shown inline after the item text
- `summary` — HTML shown in detail panel
- `icon` — visual type indicator
- `relevance` — sort order (higher = earlier in list)

---

## Completion Item Types

| Type | Relevance | Trigger |
|------|-----------|---------|
| FK/PK join condition | 10 | `detectJoinOnContext()` returns non-null |
| Procedure parameters | 5 | After EXEC with known proc name |
| Column (`alias.col`) | 0 | Expression / search-condition context |
| Table / view | 0 | Table-source context |
| Built-in functions | -1 | C3 expression rule, T-SQL BUILTIN_HINTS map |
| Clipboard IN-list | — | Caret at IN operator + clipboard data |

---

## Dialect Extension Pattern (Template Method)

To add a new SQL dialect, extend `AntlrDocumentParser<RootContextType>` and implement:

**Abstract methods (required):**
```java
Lexer   createLexer(CharStream)
Parser  createParser(TokenStream)
T       parse(Parser)                        // returns root context
Set<Integer> getCodeCompleteRuleIndices()
Set<Integer> getTableSourceRuleIndices()
Set<Integer> getProcedureFunctionsRuleIndices()
Pair<Interval, ObjectName> getTableSource(ParserRuleContext)
Pair<Interval, ObjectName> getProcedureFunction(ParserRuleContext)
```

**Hook methods (override to add behavior):**
```java
CompletionResult getDialectSpecificCompletionItems(TokenOffset)   // early exit
JoinOnContext    detectJoinOnContext(TokenOffset, String database)
boolean          isExpressionContext(ParseTree)
boolean          isTableSourceContext(ParseTree)
CompletionResult suggestColumns(ParseTree)
CompletionResult suggestTableSources()
CompletionResult getExpressionFallbackColumns(TokenOffset, ParserRuleContext, boolean)
CompletionResult getC3CompletionItems(CandidatesCollection, ParseTree, ParserRuleContext, TokenOffset)
```

The base class handles: statement context collection, token index advancement, error recovery
tiers, effective-node resolution, C3 invocation, and clipboard merging. Dialects only need
to know about their grammar.

---

## Non-Obvious Design Decisions

### Three-Tier Statement Context Resolution
ANTLR error recovery can disconnect parse tree nodes from their parents, making `parent`
traversal unreliable. The base class uses three fallback tiers:
1. **Fast path** — walk up the `parent` chain to find the enclosing statement rule
2. **Pre-collected list** — after a full parse, all statement-boundary contexts are stored in
   `statementContexts` in document order; scan backwards for the nearest one
3. **Token scan** — scan the raw token stream for statement separators / keywords

### Mini-Parse Fallback
When a DOT-triggered completion causes error recovery to break the FROM clause (e.g.
`SELECT a. FROM t`), `miniParseStatementNode()` re-parses just the current statement in
isolation. A synthetic `__x__` token is injected after the DOT to maintain valid expression
structure and prevent panic-mode recovery.

### effectiveNode vs tree vs prevTree
`tree` is the parse node at the exact caret position — it may be EOF or the first token of
the next statement when the caret is in whitespace. `prevTree` is one token back.
`effectiveNode` transparently picks the right one so downstream hooks always see a meaningful
token.

### C3 Token Index Advancement
After whitespace, `suggestTokenIndex` may point to a token that ended before the caret. The
base class advances to the next `DEFAULT_CHANNEL` token before calling C3, but never
advances past EOF — preventing C3 from computing "what follows the end of file?".

### FK Join Condition Priority
FK suggestions use `relevance: 10` (highest) so they sort to the top of the completion list.
They only fire when `detectJoinOnContext()` confirms the caret is immediately after a
`JOIN … ON` keyword — not other uses of ON (e.g. `CREATE INDEX … ON`).

### Expression Preceding Operators (T-SQL)
`SqlServerDocumentParser` maintains a set of ~20 operator tokens (`=`, `<`, `>`, `+`, `AND`,
`OR`, …). When the token before the caret is one of these, the expression-fallback flow is
activated even if the normal expression-context detection failed due to error recovery. This
enables completions in `WHERE col = |` even with a broken parse tree.

### PartialResult Flag
`CompletionResult.isPartialResult() = true` signals that the catalog is still loading.
The editor does not cache partial results and will re-query on the next keystroke.

---

## C3 Engine (CodeCompletionCore)

A fork of the open-source antlr4-c3 library. Analyses the ANTLR ATN (Augmented Transition
Network) to compute what grammar rules and tokens are syntactically valid at a given token
index. Returns `CandidatesCollection`:
- `rules` — map of grammar rule index → call stack path
- `tokens` — map of token type → follow set

`getC3CompletionItems()` in each dialect maps these rule indices to the appropriate
`suggestColumns()`, `suggestTableSources()`, or procedure-list calls.

---

## Test Structure

Tests must always be written when changing `AntlrDocumentParser` or any dialect class.

**Base test suite:** `AntlrDocumentParserTestBase`
Inherited by `SqlServerDocumentParserTest` and `PrestoDocumentParserTest`. Contains standard
scenarios: column/table suggestions, dot-trigger, EXISTS subquery scope, operator-preceded
expressions, FK/PK join conditions, statement context resolution, parse validation.

**Standard test catalogs:**

| Catalog | Contents |
|---------|----------|
| `TEST_CATALOG` | One procedure (`MyProc` with 2 params), no tables |
| `TABLE_CATALOG` | `tableA`, `tableB` with columns + same procedure |
| `FK_CATALOG` | Three tables with FK relationships for JOIN ON tests |

**Helper methods:**
- `complete(sql)` — returns `List<CompletionItem>` with caret at `|` marker in the SQL string
- `replacements(items)` — extracts replacement texts for assertion
- `aliasedColumns(items)` — extracts `alias.column` pairs
