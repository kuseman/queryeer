# ANTLR SQL Server Parsing & Code Completion

> **IMPORTANT:** This document covers `SqlServerDocumentParser` (T-SQL dialect) specifics.
> It must be kept up to date whenever changes are made to `SqlServerDocumentParser` or
> `SqlServerDocumentParserTest`. It is loaded by AI agents to avoid expensive re-analysis
> of this complex code.
>
> For the generic base-class architecture see [ANTLR_PARSING.md](./ANTLR_PARSING.md).

## Relevant Files

| File | Description |
|------|-------------|
| `queryeer-catalog/.../dialect/SqlServerDocumentParser.java` | T-SQL dialect implementation |
| `queryeer-catalog/.../test/.../SqlServerDocumentParserTest.java` | T-SQL specific tests |

---

## Completion Item Types (T-SQL specific)

| Type | Relevance | Trigger |
|------|-----------|---------|
| Built-in functions | -1 | C3 expression rule, T-SQL `BUILTIN_HINTS` map |

---

## Non-Obvious Design Decisions (T-SQL)

### Expression Preceding Operators
`SqlServerDocumentParser` maintains a set of ~20 operator tokens (`=`, `<`, `>`, `+`, `AND`,
`OR`, …). When the token before the caret is one of these, the expression-fallback flow is
activated even if the normal expression-context detection failed due to error recovery. This
enables completions in `WHERE col = |` even with a broken parse tree.

### Table-Source Dotted Qualifier Fallback
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

### INSERT / UPDATE / DELETE Table-Source via C3 (`RULE_ddl_object`)
INSERT INTO, UPDATE, and DELETE use `ddl_object` (→ `full_table_name`) as their target table
reference, NOT `table_source_item`. This means `isTableSourceContext()` (which walks up to
`Table_source_itemContext`) never fires for these positions.

- **Dotted case** (`INSERT INTO dbo.|`): handled by `isInsideTableSourceDottedQualifier()` in
  `getDialectSpecificCompletionItems` — the token-stream scan finds `INTO` before `dbo.` and
  returns `suggestTableSources()` early.
- **Non-dotted case** (`INSERT INTO |`): `RULE_ddl_object` is included in
  `getCodeCompleteRuleIndices()` so C3 stops there and returns it as a candidate.
  `getC3CompletionItems()` handles `RULE_ddl_object` → `suggestTableSources()`.

**Why not `RULE_full_table_name`?** `full_table_name` also appears inside `full_column_name`
(`full_table_name '.' column_name`). Adding it to the preferred rules would cause C3 to fire
table-source suggestions in column-name positions too. `RULE_ddl_object` is used exclusively
in INSERT / UPDATE / DELETE and does not appear in column-name contexts, making it safe.

---

## Diagnosing Completion Bugs (T-SQL specific)

See [ANTLR_PARSING.md — Diagnosing Completion Bugs](./ANTLR_PARSING.md#diagnosing-completion-bugs)
for the general debug pattern (token stream dump, `TokenOffset` inspection).

**T-SQL failure signatures:**

- **Procedure parameters appear in a `FROM` clause after an EXEC statement** —
  `findExecuteBodyByOffset` only has a lower-bound check (`caretOffset > procName.stop.getStopIndex()`)
  and no upper bound. The whole-tree scan would match the earlier EXEC body even when the
  caret is in a later statement. Fix: restrict the search root to
  `findNearestPrecedingStatementCtx(caretOffset)` so only the statement that actually contains
  the caret is searched.
- **No completions for `INSERT INTO |` (non-dotted)** — `isTableSourceContext()` only detects
  `Table_source_itemContext` in the tree; INSERT's target is a `ddl_object` (not a
  `table_source_item`). The dotted case (`INSERT INTO dbo.|`) is handled by
  `isInsideTableSourceDottedQualifier()`. The non-dotted case fell through all checks and C3
  also missed it because `RULE_ddl_object` was not in `getCodeCompleteRuleIndices()`. Fix: add
  `RULE_ddl_object` to the preferred rules and handle it in `getC3CompletionItems()`. The same
  issue applies to `UPDATE |` and `DELETE FROM |` since they also use `ddl_object`.
