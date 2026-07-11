# Setun

[![CI](https://github.com/Caleb-Wrobel/setun/actions/workflows/ci.yml/badge.svg)](https://github.com/Caleb-Wrobel/setun/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/Caleb-Wrobel/setun/graph/badge.svg)](https://codecov.io/gh/Caleb-Wrobel/setun)
[![Scala 3](https://img.shields.io/badge/scala-3.7.1-DC322F.svg)](https://www.scala-lang.org/)

An emulator for the Soviet [Setun-70](https://en.wikipedia.org/wiki/Setun)
balanced ternary computer, in Scala 3. Built as the reference substrate
for a future original ternary engine, *tritium* — hence the `io.tritium`
organization.

## Status

- ✅ `setun-core`: trit and tryte (6-trit, ±364) arithmetic with a DSL
  (`t"+0-"` string-interpolated literals, `42.bt`)
- ✅ 18-trit `Word`: ternary multiplication, shifts (`w"..."`, `Long.bw`)
- ✅ Instruction set spec: `docs/setun-70/instruction-set.md` (81 ops, encoding, modes)
- ✅ Machine model, phase 1 (`setun-machine`): paged memory, decode of
  all tryte forms, pure `step` interpreter running B1–B27 + register specials
- ✅ Ternary assembly DSL (`setun.machine.asm`): mnemonics, labels,
  deduplicated constant pool, assembles to bootable memory pages
- ✅ Macro-operations (system calls): page −13 dispatch, cb/cc linkage,
  CMC/RMC/LMC, mode enforcement
- ✅ Drum paging (LQ/CF/LF) and I/O channels (CG/LU/LG): the full
  instruction set runs; devices are modeled purely (drums as page maps,
  channels as input queues and output logs a driver fills and drains)

## Quick start

```bash
sbt test
```

```scala
import setun.core.*
import setun.core.dsl.*

val a = t"+0-"      // 8
val b = 34.bt       // +0-0+
a + b               // ++-0-0 (42)
```

Assemble and run a Setun-70 program:

```scala
import setun.machine.*, setun.machine.asm.Asm, Asm.*

val prog = Asm.page(5):
  push(const(5)); push(const(7))
  op(BasicOp.SAddT)                   // 12
  push(const(3)); op(BasicOp.LMulT)   // R := 12, S:Y := 3 × 12 × 3²
  push(constSenior(16)); op(BasicOp.LST)

Machine.run(prog.boot, 7).operands.head.toLong  // 36
```

The full instruction set runs — macro-operations (system calls), drum
paging, and I/O channels included. A macro handler on page −13 that
echoes its dispatch parameter to an output channel:

```scala
val sys = Asm.page(-13):
  data(-27, Seq(Tryte.fromInt(7))) // dispatch parameter for macro 0
  org(-13)                         // common handler entry m[−13,−13]
  op(SpecialOp.LG2)                // write the parameter to channel 2
  op(SpecialOp.RMC)                // return to user code

val user = Asm.page(5) { macroOp(0) }

val mem = Memory(Map(-13 -> sys.page, 5 -> user.page))
val m0  = MachineState.initial(mem).copy(c1 = Trit.N, cPage = 5) // user mode
Machine.run(m0, 3).channels(Trit.Z).output  // Vector(7·3¹² — the parameter, as a word)
```

Devices are pure state: drums are page maps, channels are input queues
and output logs — a driver (or test) fills and drains them between
steps, so any `MachineState` snapshot is replayable.

## AI-native setup

This repo is configured for Claude Code (see `CLAUDE.md`), with an MCP
bridge (`tools/local-llm-mcp/`) that lets Claude delegate mundane lookups
to an on-prem Open WebUI instance. Set in your environment:

```bash
export OPENWEBUI_URL=http://<your-box>:3000
export OPENWEBUI_API_KEY=<key from Open WebUI Settings → Account>
export LOCAL_MODEL=<model id>   # optional; defaults to first available
```

## Reference material

Setun-70 documents live in `docs/setun-70/`.
