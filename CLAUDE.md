## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- ALWAYS read graphify-out/GRAPH_REPORT.md before reading any source files, running grep/glob searches, or answering codebase questions. The graph is your primary map of the codebase.
- IF graphify-out/wiki/index.md EXISTS, navigate it instead of reading raw files
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

# Context+ MCP - Agent Workflow

## Purpose

Context+ gives you structural awareness of the entire codebase without reading every file. These tools replace your default search and read operations — use them as your primary interface to the codebase.

## PMLL Short-Term KV Memory (5 Tools)

Before every expensive MCP tool invocation, agents MUST use the PMLL short-term KV memory tools to check the cache. This eliminates redundant calls and accelerates task execution. These tools are provided by the [`pmll-memory-mcp`](https://www.npmjs.com/package/pmll-memory-mcp) server (see [mcp/README.md](./README.md)).

| Tool      | Input                                              | Description                                                |
|-----------|----------------------------------------------------|------------------------------------------------------------|
| `init`    | `session_id: str`, `silo_size: int = 256`          | Initialize a PMLL silo and Q-promise chain for the session |
| `peek`    | `session_id: str`, `key: str`                      | Non-destructive cache lookup + Q-promise check (call before every expensive tool) |
| `set`     | `session_id: str`, `key: str`, `value: str`        | Store a key-value pair in the silo after a cache miss      |
| `resolve` | `session_id: str`, `promise_id: str`               | Check or resolve a Q-promise continuation                  |
| `flush`   | `session_id: str`                                  | Clear all silo slots at task completion                    |

### The `peek()` Pattern

Call `peek` before every expensive tool invocation:

1. **`init`** once at task start to set up the session silo
2. **`peek`** before each expensive call — if hit, use the cached value; if pending, wait on the Q-promise
3. **`set`** after a cache miss to populate the silo for future agents/subtasks
4. **`resolve`** to check or fulfill Q-promise continuations
5. **`flush`** at task end to clear all session slots

This pattern ensures that Context+ tool results, Playwright page contents, and other expensive outputs are cached and reused across subtasks rather than re-fetched.

## Tool Priority (Mandatory)

You MUST use Context+ tools instead of native equivalents. Only fall back to native tools when a Context+ tool cannot fulfill the specific need.

| Instead of…              | MUST use…                    | Why                                          |
|--------------------------|------------------------------|----------------------------------------------|
| `grep`, `rg`, `ripgrep`  | `semantic_code_search`       | Finds by meaning, not just string match      |
| `find`, `ls`, `glob`     | `get_context_tree`           | Returns structure with symbols + line ranges |
| `cat`, `head`, read file | `get_file_skeleton` first    | Signatures without wasting context on bodies |
| manual symbol tracing    | `get_blast_radius`           | Traces all usages across the entire codebase |
| keyword search           | `semantic_identifier_search` | Ranked definitions + call chains             |
| directory browsing       | `semantic_navigate`          | Browse by meaning, not file paths            |

## Workflow

1. Start every task with `get_context_tree` or `get_file_skeleton` for structural overview
2. Use `semantic_code_search` or `semantic_identifier_search` to find code by meaning
3. Run `get_blast_radius` BEFORE modifying or deleting any symbol
4. Prefer structural tools over full-file reads — only read full files when signatures are insufficient
5. Run `run_static_analysis` after writing code
6. Use `search_memory_graph` at task start for prior context, `upsert_memory_node` after completing work

## Execution Rules

- Think less, execute sooner: make the smallest safe change that can be validated quickly
- Batch independent reads/searches in parallel — do not serialize them
- If a command fails, diagnose once, pivot strategy, continue — cap retries to 1-2
- Keep outputs concise: short status updates, no verbose reasoning

## Tool Reference

### PMLL Short-Term KV Memory

| Tool      | When to Use                                                                  |
|-----------|------------------------------------------------------------------------------|
| `init`    | Once at task start. Set up the PMLL silo and Q-promise chain for the session.|
| `peek`    | Before every expensive MCP tool call. Non-destructive cache + Q-promise check.|
| `set`     | After a cache miss. Store the result so future agents/subtasks skip the call. |
| `resolve` | When a Q-promise is pending. Check or fulfill the continuation.              |
| `flush`   | At task end. Clear all silo slots for the session.                           |

### GraphQL

| Tool      | When to Use                                                                  |
|-----------|------------------------------------------------------------------------------|
| `graphql` | Execute GraphQL queries/mutations against the memory store with optional PMLL cache integration. |

### Context+ Structural Tools

| Tool                        | When to Use                                                  |
|-----------------------------|--------------------------------------------------------------|
| `get_context_tree`          | Start of every task. Map files + symbols with line ranges.   |
| `get_file_skeleton`         | Before full reads. Get signatures + line ranges first.       |
| `semantic_code_search`      | Find relevant files by concept.                              |
| `semantic_identifier_search`| Find functions/classes/variables and their call chains.      |
| `semantic_navigate`         | Browse codebase by meaning, not directory structure.         |
| `get_blast_radius`          | Before deleting or modifying any symbol.                     |
| `get_feature_hub`           | Browse feature graph hubs. Find orphaned files.              |
| `run_static_analysis`       | After writing code. Catch errors deterministically.          |
| `propose_commit`            | Validate and save file changes.                              |
| `list_restore_points`       | See undo history.                                            |
| `undo_change`               | Revert a change without touching git.                        |

### Long-Term Memory Graph

| Tool                        | When to Use                                                  |
|-----------------------------|--------------------------------------------------------------|
| `upsert_memory_node`        | Create/update memory nodes (concept, file, symbol, note).    |
| `create_relation`           | Create typed edges between memory nodes.                     |
| `search_memory_graph`       | Semantic search + graph traversal across neighbors.          |
| `prune_stale_links`         | Remove decayed edges and orphan nodes.                       |
| `add_interlinked_context`   | Bulk-add nodes with auto-similarity linking.                 |
| `retrieve_with_traversal`   | Walk outward from a node, return scored neighbors.           |

### Solution Engine

| Tool                   | When to Use                                                           |
|------------------------|-----------------------------------------------------------------------|
| `resolve_context`      | Unified context lookup — checks short-term KV first, falls back to long-term semantic graph. |
| `promote_to_long_term` | Promote a frequently-accessed short-term KV entry to the long-term memory graph. |
| `memory_status`        | Get a unified view of both short-term (KV cache) and long-term (semantic graph) memory layers. |

## Anti-Patterns

1. Reading entire files without checking the skeleton first
2. Deleting functions without checking blast radius
3. Running independent commands sequentially when they can be parallelized
4. Repeating failed commands without changing approach
5. Calling expensive MCP tools without calling `peek` first to check the cache
6. Forgetting to call `init` at task start or `flush` at task end, causing silent cache misses or stale data across sessions
7. Storing frequently-accessed payloads only in short-term KV instead of promoting them to long-term memory with `promote_to_long_term`
8. Calling `search_memory_graph` or `retrieve_with_traversal` directly instead of using `resolve_context`, which checks both memory layers in one call
9. Ignoring Q-promise `pending` status from `peek` and re-issuing the same expensive call instead of waiting with `resolve`
