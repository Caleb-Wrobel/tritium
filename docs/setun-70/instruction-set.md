# Setun-70 instruction set (original, pre-1975)

English rendering of `Описание_наборa_инструкций_Сетунь_70.pdf`
(“Description of the original Setun-70 command system (before the
modifications dated 1975)”, © 2023 Stanislav Maslovsky). This is the
authoritative spec for the emulator; when in doubt, check the PDF.

## Encoding

Machine code is a sequence of 6-trit trytes `k = k1,k2,k3,k4,k5,k6`.

- `k1,k2 = 0,0` and `k3 = 0` → a **basic** operation (B1–B27).
- `k1,k2 = 0,0` and `k3 = +` → a **special** operation (S1–S27).
- `k1,k2,k3 = 0,0,-` → a **macro-operation** (system call).
- any other `k1,k2` → an **operand reference**, pushing an operand from
  memory onto the operand stack:
  - `k1`: operand length in trytes = `k1 + 2` (so 1–3 trytes)
  - `k2`: selects the page register used for addressing: `h[k2]`
  - `k[3:6]`: address of the operand's first tryte within the page
  - A syllable address with `k[1:2] = 0` is inadmissible, so with page
    register `h[0]` (i.e. `k2 = 0`) the operand length cannot be 2.
  - Operands are stored big-endian (most significant trytes first).

The “9-code” column is the tryte in balanced nonary (digit pairs of
trits): `W X Y Z 0 1 2 3 4` stand for −4…+4.

## Machine-state notation

- `T` — top word of the operand stack (words are 3 trytes / 18 trits);
  `t` is its first (most significant) tryte.
- `S` — sub-top word of the operand stack.
- `Y`, `R` — extension and multiplier registers (18 trits).
- `e` — exponent register (6 trits).
- `c` — instruction pointer state; `ca` — address part; `cb`, `cc` —
  macro-call linkage; `c1` — mode trit.
- `ph:pa` — operand stack pointer (page:address); `p` holds current and
  reserve pointers.
- `h[-1], h[0], h[+1]` — page (base) registers.
- `f[i,·]` — external page memory (drums, МБ1–МБ3); `q[i]` — external
  page number registers; `g[i]`, `u[i]`, `a[i]` — I/O channel data,
  control, and active registers.
- `↓` — the stack pointer moves down one word after the operation: the
  old sub-top `S` becomes the new top `T`.

## Basic operations (B1–B27)

| # | Mnemonic | 3-code | 9-code | Semantics | Notes |
|---|----------|--------|--------|-----------|-------|
| B1 | `LST` | `000---` | 0ZW | Shift the pair `S:Y` by `t` digits; ↓ | left if `t > 0`, right if `t < 0` |
| B2 | `COT` | `000--0` | 0ZX | Jump by offset in `t` if `\|S\| > 3^17/2` (overflow); ↓ | `ca := t` on overflow |
| B3 | `XNN` | `000--+` | 0ZY | Normalize the number with mantissa in `T:Y` and exponent in `e` | |
| B4 | `E-1` | `000-0-` | 0ZZ | `e := e − 1` | |
| B5 | `E=0` | `000-00` | 0Z0 | `e := 0` | |
| B6 | `E+1` | `000-0+` | 0Z1 | `e := e + 1` | |
| B7 | `T-E` | `000-+-` | 0Z2 | `T := T − e × 3^12` | |
| B8 | `E=T` | `000-+0` | 0Z3 | `e := t`; ↓ | |
| B9 | `T+E` | `000-++` | 0Z4 | `T := T + e × 3^12` | |
| B10 | `CLT` | `0000--` | 00W | If `S < 0`, jump by offset in `t`; ↓ | `ca := t` if taken |
| B11 | `CET` | `0000-0` | 00X | If `S = 0`, jump by offset in `t`; ↓ | `ca := t` if taken |
| B12 | `CGT` | `0000-+` | 00Y | If `S > 0`, jump by offset in `t`; ↓ | `ca := t` if taken |
| B13 | `T=C` | `00000-` | 00Z | Clear `T` and put the address of the current instruction into `t` | |
| B14 | `R=T` | `000000` | 000 | `R := T`; ↓ | |
| B15 | `C=T` | `00000+` | 001 | Unconditional jump within the page by offset in `t`; ↓ | `ca := t` |
| B16 | `T=W` | `0000+-` | 002 | Copy trytes from RAM/ROM cells (reference in `t`) to the stack top | exception (`w = −29`) if outside the page |
| B17 | `YFT` | `0000+0` | 003 | Clear `Y`; ↓ | |
| B18 | `W=S` | `0000++` | 004 | Copy trytes from the stack sub-top to RAM cells (reference in `t`); ↓ | exception (`w = −29`) if outside the page |
| B19 | `SMT` | `000+--` | 01W | `S :=` tritwise multiplication of `T` and `S`; ↓ | |
| B20 | `Y=T` | `000+-0` | 01X | `Y := T`; ↓ | |
| B21 | `SAT` | `000+-+` | 01Y | `S :=` tritwise threshold addition of `T` and `S`; ↓ | |
| B22 | `S-T` | `000+0-` | 01Z | `S := S − T`; ↓ | |
| B23 | `TDN` | `000+00` | 010 | `T := −T` | |
| B24 | `S+T` | `000+0+` | 011 | `S := S + T`; ↓ | |
| B25 | `LBT` | `000++-` | 012 | `S:Y := S × 3^18 + T × R × 3^2`; ↓ | |
| B26 | `L*T` | `000++0` | 013 | `R := S`, `S:Y := T × R × 3^2`; ↓ | |
| B27 | `LHT` | `000+++` | 014 | `S:Y := S × 3^18 + Y + T × R × 3^2`; ↓ | |

## Special operations (S1–S27)

| # | Mnemonic | 3-code | 9-code | Semantics | Notes |
|---|----------|--------|--------|-----------|-------|
| S1 | `CG1` | `00+---` | 02W | Read channel 1 to stack top: `T := 0`, `t := ±g[1,1:6]` | plus when `g[-1,7] = 1`, minus when `0` |
| S2 | `CG2` | `00+--0` | 02X | Read channel 2 to stack top: `T := 0`, `t := ±g[2,1:6]` | plus when `g[0,7] = 1`, minus when `0` |
| S3 | `CG3` | `00+--+` | 02Y | Read channel 3 to stack top: `T := 0`, `t := ±g[3,1:6]` | plus when `g[+1,7] = 1`, minus when `0` |
| S4 | `CF1` | `00+-0-` | 02Z | Copy external page `f[-1,q[-1]]` into RAM at pointer from `t`; ↓ | |
| S5 | `CF2` | `00+-00` | 020 | Copy external page `f[0,q[0]]` into RAM at pointer from `t`; ↓ | |
| S6 | `CF3` | `00+-0+` | 021 | Copy external page `f[+1,q[+1]]` into RAM at pointer from `t`; ↓ | |
| S7 | `LQ1` | `00+-+-` | 022 | `q[-1] := T[5:12]`, `SUIT(-1)` | starts async page search on МБ1 |
| S8 | `LQ2` | `00+-+0` | 023 | `q[0] := T[5:12]`, `SUIT(0)` | starts async page search on МБ2 |
| S9 | `LQ3` | `00+-++` | 024 | `q[+1] := T[5:12]`, `SUIT(+1)` | starts async page search on МБ3 |
| S10 | `CP` | `00+0--` | 03W | Copy stack pointer `ph:pa` to stack top | `z := 0; z[5:6] := ph; z[9:11] := pa; T := z` |
| S11 | `EXP` | `00+0-0` | 03X | Exchange current stack pointer with the reserve one | `z[1:5] := p[1:5]; p[1:5] := p[6:10]; p[6:10] := z[1:5]` |
| S12 | `LP` | `00+0-+` | 03Y | Load stack pointer from stack top | `z := T; ph := z[5:6]; pa := z[9:11]` |
| S13 | `CMC` | `00+00-` | 03Z | Copy `cb` to stack top; exchange contents of `cb` and `cc` | |
| S14 | `RMC` | `00+000` | 030 | Return from macro-operation | `z[1:12] := c[9:20]; c[9:20] := c[21:32]; c[21:32] := z[1:12]; c[1:4] := z[3:6]; c[5:8] := z[9:12]` |
| S15 | `LMC` | `00+00+` | 031 | Load `cb` from stack top, old `cb` → `cc`; ↓ | `z := T; c[21:32] := c[9:20]; c[9:20] := z[1:12]` |
| S16 | `LH1` | `00+0+-` | 032 | Load page register `h[-1]` from stack top; ↓ | `z := T; h[-1] := z[4:6]` |
| S17 | `LH2` | `00+0+0` | 033 | Load page register `h[0]` from stack top; ↓ | `z := T; h[0] := z[4:6]` |
| S18 | `LH3` | `00+0++` | 034 | Load page register `h[+1]` from stack top; ↓ | `z := T; h[+1] := z[4:6]` |
| S19 | `LU1` | `00++--` | 04W | Load control register of I/O channel 1; ↓ | `u[-1,1:4] := t; a[-1] := 1` |
| S20 | `LU2` | `00++-0` | 04X | Load control register of I/O channel 2; ↓ | `u[0,1:4] := t; a[0] := 1` |
| S21 | `LU3` | `00++-+` | 04Y | Load control register of I/O channel 3; ↓ | `u[+1,1:4] := t; a[+1] := 1` |
| S22 | `LF1` | `00++0-` | 04Z | Unload page numbered from `t` to external memory `f[-1,q[-1]]` | |
| S23 | `LF2` | `00++00` | 040 | Unload page numbered from `t` to external memory `f[0,q[0]]` | |
| S24 | `LF3` | `00++0+` | 041 | Unload page numbered from `t` to external memory `f[+1,q[+1]]` | |
| S25 | `LG1` | `00+++-` | 042 | Write to channel 1 from stack top; ↓ | `TRANSOUT(-1)` |
| S26 | `LG2` | `00+++0` | 043 | Write to channel 2 from stack top; ↓ | `TRANSOUT(0)` |
| S27 | `LG3` | `00++++` | 044 | Write to channel 3 from stack top; ↓ | `TRANSOUT(+1)` |

## Modes (register `c1`)

- `c1 = −1` — **user mode**: basic operations (B1–B27) and
  macro-operations allowed. Interrupts enabled.
- `c1 = 0` — **macro-operation mode**: entered when a macro-operation
  code is executed. Basic and special (S1–S27) operations allowed; new
  macro-operations forbidden. Interrupts enabled.
- `c1 = +1` — **system mode**: basic and special operations available.
  The machine starts in this mode. Interrupts and macro-operations
  forbidden.

## Macro-operation (system call) mechanics

Executing a macro-operation saves the current `cb` into `cc` and `c`
into `cb`, pushes onto the stack the syllable `c t = m[-13, ka]` (where
`ka = k[3:6]` from the macro-operation code), and jumps to the
instruction at `m[-13, -13]`. Return is via `RMC`, which restores the
previously saved `c` from `cb`.

## Memory (from the register diagram)

- ПЗУ (ROM): 18 pages; ОЗУ (RAM): 9 pages; МБ (drum): 243 pages ×3
  channels. Exchange with the stack is word-wise, three trytes at a
  time.
