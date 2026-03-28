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
- **No table suggestions in `EXISTS ( SELECT … FROM | )` subquery** — the closing `)` of
  the EXISTS is parsed inside the outer query's `PredicateContext`. `isExpressionContext(tree=')')`
  returns true, which fires `suggestColumns()` (returning null — no aliases outside the EXISTS)
  and sets `expressionContextDetected=true`. The correct `RULE_table_source_item` is never
  returned by C3 because `translateToRuleIndex` scans the callStack from the outermost rule and
  hits `RULE_search_condition` (outer WHERE) before it reaches `RULE_table_source_item` (inner
  FROM). Fix: add `isDirectlyAfterTableSourceKeyword()` check in `getDialectSpecificCompletionItems`
  that fires in step 1 (before `isExpressionContext` in step 3). The check looks for the nearest
  preceding DEFAULT-channel token; if it is `FROM`, `JOIN`, `INTO`, `UPDATE`, `APPLY`, or `MERGE`,
  return `suggestTableSources()` immediately.
- **No column suggestions for `SELECT | FROM dbo.tableA t`** (empty SELECT list) — ANTLR
  error recovery detaches `dbo.tableA t` from the `Query_specificationContext` when no
  `select_list` is present, making the `table_sources` inaccessible. The mini-parse in
  `getC3CompletionItems` reruns on the same broken text (no injection because the caret is in
  whitespace, not after a DOT) and finds the same empty aliases. Fix: extend
  `miniParseStatementNode` to inject `__x__ ` (identifier + space) at the caret when
  `text.charAt(miniCaret-1)` is whitespace and the caret is not at the end of the text. The
  injected text turns `"SELECT  FROM …"` into `"SELECT __x__  FROM …"` — syntactically valid —
  so ANTLR parses the FROM clause properly and `collectTableSourceAliases` finds the alias.
- **No column suggestions for `WHERE a.col1` inside an EXISTS subquery WHERE clause** — the
  bare expression `a.col1` without a comparison operator is not a valid T-SQL `search_condition`,
  so ANTLR's panic-mode recovery detaches it from the inner `query_specification` and places it
  in `Execute_body_batchContext` (same pattern as the UPDATE WHERE dot-trigger bug). The mini-
  parse in the dotted-qualifier path re-parses the same broken text because the caret is after
  `col1` (not after a DOT), so the existing DOT-based injection does not trigger. Fix: extend
  `miniParseStatementNode` to also inject ` = 1` when `text.charAt(miniCaret-1)` is an
  identifier character AND looking backward through the current identifier finds a DOT
  (indicating an `alias.col` dotted qualifier). Result: `"WHERE a.col1 = 1\n)"` is a valid
  predicate and ANTLR keeps it inside the inner `query_specification`.
- **"Missing table 't'" false positive for `DELETE t FROM table t WHERE …`** — `ValidateVisitor.visitDdl_object`
  already skipped validation inside `Update_statementContext` (because `UPDATE a FROM table a`
  uses an alias as the UPDATE target). The same pattern exists for DELETE: `DELETE t FROM #t_articlesToSync t`
  puts alias `t` in `delete_statement_from`, which is a `ddl_object`. Without the guard, the
  validator looks up `t` as a real table name and reports "Missing table 't'". Fix: in the
  `visitDdl_object` parent-walk loop, also return early when `current instanceof Delete_statementContext`
  **and** `dCtx.table_sources() != null` (i.e., there is a FROM clause, confirming the delete
  target is an alias, not a real table).
