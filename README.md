# Tritium

A balanced ternary computational engine in Scala 3, using the Soviet
[Setun-70](https://en.wikipedia.org/wiki/Setun) as reference architecture.

## Status

- ✅ `tritium-core`: trit and tryte (6-trit, ±364) arithmetic with a DSL
  (`t"+0-"` string-interpolated literals, `42.bt`)
- ✅ 18-trit `Word`: ternary multiplication, shifts (`w"..."`, `Long.bw`)
- ✅ Instruction set spec: `docs/setun-70/instruction-set.md` (81 ops, encoding, modes)
- ⬜ Setun-70 two-stack machine emulator
- ⬜ Ternary assembly DSL

## Quick start

```bash
sbt test
```

```scala
import tritium.core.*
import tritium.core.dsl.*

val a = t"+0-"      // 8
val b = 34.bt       // +0-0+
a + b               // ++-0-0 (42)
```

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
