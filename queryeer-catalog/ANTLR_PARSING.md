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
- `prevTree` — previous terminal node in DFS traversal order (see warning below)

`effectiveNode` is derived from these: if the caret is in whitespace/hidden channel, it falls
back to `prevTree` so the completion logic always works against a real token.

**Warning: `prevTree` may be a synthetic error-recovery token, not the preceding real token.**
`findTokenFromOffset` does a DFS over the parse tree and sets `prevTree` to whichever
`TerminalNode` it visited immediately before `tree`. ANTLR's `getMissingSymbol()` recovery
inserts synthetic tokens (e.g. `<missing 'ON'>`) into the parse tree with `startIndex = -1`.
These nodes don't match any caret offset so they don't stop the DFS, but they do update
`prevTree`. In a multi-join query like `FROM t a INNER JOIN dbo.|` where the ON clause is
also absent, the synthetic ON token can appear before the real DOT in DFS order, making
`prevTree` point to the synthetic ON rather than the `dbo` identifier.
Consequence: **never rely solely on `prevTree` for context detection**; always cross-check
against the token stream directly (e.g. via `parser.getInputStream().get(suggestTokenIndex)`)
when tree-based checks return unexpected results.

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
the next statement when the caret is in whitespace. `prevTree` is the DFS-previous terminal
node (see the `TokenOffset` warning above — it may be a synthetic error-recovery token).
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

### Table-Source Dotted Qualifier Fallback (T-SQL)
In multi-join queries like `SELECT … FROM t a INNER JOIN dbo.|`, ANTLR error recovery can
detach the DOT from its `Table_source_itemContext` (e.g. when the ON clause is also missing,
causing the recovery to insert a synthetic `ON` token that appears *before* the DOT in the
DFS traversal order). `isTableSourceContext()` then returns false for both `tree` and
`prevTree`, so the normal tree-walk path misses the table-source suggestion.

`getDialectSpecificCompletionItems` handles this with `isInsideTableSourceDottedQualifier()`:
a token-stream scan that walks backward past the identifier chain (ID/DOT tokens) and checks
whether the preceding keyword is `FROM`, `JOIN`, `INTO`, `UPDATE`, `APPLY`, or `MERGE`. If
so, `suggestTableSources()` is returned early, bypassing the (incorrect) column-suggestion
path that `isInsideDottedQualifier` would otherwise trigger.

### Parse-Tree Checks vs Token-Stream Scans
Two complementary techniques are used for context detection, with different reliability
profiles under error recovery:

| Technique | Reliable when… | Breaks when… |
|-----------|---------------|--------------|
| Walk `parent` chain (e.g. `isTableSourceContext`, `isExpressionContext`) | Tree is intact | Error recovery detaches a node or inserts synthetic siblings that shift DFS order |
| Scan `parser.getInputStream()` backward (e.g. `isInsideDottedQualifier`, `isInsideTableSourceDottedQualifier`) | Always — the token stream is never restructured by error recovery | Token is on a non-DEFAULT channel (always skip hidden-channel tokens) |

**Rule of thumb:** use tree-walking for the common case (fast, grammar-aware); add a
token-stream fallback whenever a DOT-triggered completion must work in positions where the
surrounding grammar rule is incomplete (no alias after a JOIN, no ON clause, etc.).

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

---

## Diagnosing Completion Bugs

When a completion position returns wrong or empty results, the fastest path to the root cause
is to add a temporary test in `SqlServerDocumentParserTest` (or the relevant dialect test)
that prints the internal state at the failing offset:

```java
documentParser.parse(new StringReader(query));
SqlServerDocumentParser p = sqlServerDocumentParser;  // protected field access (same package)

// 1. Dump token stream to see what ANTLR actually lexed (including synthetic tokens)
for (int i = 0; ; i++) {
    Token t = p.parser.getInputStream().get(i);
    System.out.printf("Token[%d] type=%d ch=%d text='%s' start=%d%n",
        i, t.getType(), t.getChannel(), t.getText(), t.getStartIndex());
    if (t.getType() == Token.EOF) break;
}

// 2. Inspect what findTokenFromOffset resolved for the caret position
TokenOffset off = AntlrDocumentParser.findTokenFromOffset(p.parser, p.context, caretOffset);
System.out.println("suggestTokenIndex=" + off.suggestTokenIndex());
// tree/prevTree may be synthetic — check their text, not just type
if (off.tree() instanceof TerminalNode tn)
    System.out.println("tree  type=" + tn.getSymbol().getType() + " text='" + tn.getSymbol().getText() + "'");
if (off.prevTree() instanceof TerminalNode tn)
    System.out.println("prev  type=" + tn.getSymbol().getType() + " text='" + tn.getSymbol().getText() + "'");
```

**What to look for:**

- `prevTree text='<missing 'X'>'` — a synthetic error-recovery token shifted `prevTree` away
  from the expected real token; tree-based context checks will be unreliable. Use token-stream
  scans instead (see `isInsideDottedQualifier`, `isInsideTableSourceDottedQualifier`).
- `suggestTokenIndex=-1` — `findTokenFromOffset` could not resolve the caret to any token;
  check whether the caret offset is past the end of the document.
- Wrong items returned — trace which step in the flow fired by temporarily adding
  `System.out.println` before each `return` in `getCompletionItems(TokenOffset)` and in
  `getDialectSpecificCompletionItems`. The step that returns first is the culprit.
- **Procedure parameters appear in a `FROM` clause after an EXEC statement** — `findExecuteBodyByOffset`
  only has a lower-bound check (`caretOffset > procName.stop.getStopIndex()`) and no upper bound.
  The whole-tree scan would match the earlier EXEC body even when the caret is in a later statement.
  Fix: restrict the search root to `findNearestPrecedingStatementCtx(caretOffset)` so only the
  statement that actually contains the caret is searched.
