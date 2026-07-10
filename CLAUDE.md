# Setun — a Setun-70 emulator in Scala 3

A pure emulator of the Setun-70 balanced ternary computer: core model
(trits, trytes, words, arithmetic), a Scala DSL for ternary programs, and
the two-stack machine itself. The name "tritium" is reserved for Caleb's
future original ternary engine (this project serves it, hence the
`io.tritium` org); don't reintroduce "tritium" into package or module
names here.

## Build & test

- `sbt compile` — build everything
- `sbt test` — run all tests (munit)
- `sbt "core/testOnly setun.core.*"` — core tests only

## Layout

- `modules/core` — trit/tryte/word arithmetic and the DSL
  (`setun.core.dsl`: `t"+0-"`/`w"..."` literals, `Int.bt`, `Long.bw`).
  Trits are `-`/`0`/`+`; a tryte is 6 trits, a word 18, MSB first.
- `modules/machine` — the Setun-70 emulator: `Memory` (27 pages × 81
  trytes, pages −13…+13, RAM from +5), `Instruction.decode`, and
  `Machine.step`, a pure `MachineState => MachineState`. The spec is
  `docs/setun-70/instruction-set.md`; deviations must be commented.
  Phase 2 (macro-ops, drum paging, I/O) currently faults `Unimplemented`.
- `docs/setun-70/` — reference material on the Setun-70 architecture.
  Consult these before making architectural decisions about the emulator.
- `tools/local-llm-mcp/` — MCP bridge to the on-prem Open WebUI instance.

## Model delegation policy

An on-prem LLM is available via the `local-llm` MCP server. To conserve
Claude usage, delegate mundane subtasks to it with the `local_llm` tool:

- dictionary/terminology lookups (ternary logic terms, Russian computing
  history vocabulary)
- summarizing or extracting facts from reference documents in `docs/`
- boilerplate prose (docstring drafts, README sections to be reviewed)

Do NOT delegate: code that will be committed, architectural decisions,
anything requiring correctness (ternary arithmetic reasoning). Verify
local-model output before relying on it. If the MCP server is unreachable
(e.g. cloud session off the LAN), just do the task yourself.

## Conventions

- Scala 3 syntax (significant indentation, `enum`, extension methods).
- Balanced ternary display convention: `-`, `0`, `+`, MSB first.
- Tests: munit; prefer exhaustive/property-style checks over examples —
  tryte range is small enough to test exhaustively.
