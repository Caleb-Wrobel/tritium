# Stack machine theory — a survey

Background research for the setun emulator and, eventually, the original
tritium engine. The Setun-70 is a two-stack machine; this note places that
design in the broader theory so later architectural choices are made
deliberately.

## 1. What a stack machine is

A stack machine is a computer whose instructions implicitly address the
top of a pushdown stack instead of naming registers. The canonical
taxonomy (Koopman, *Stack Computers: The New Wave*, 1989) classifies
ISAs by operand addressing:

- **3-address** (register machines): `add r1, r2, r3` — all operands named.
- **1-address** (accumulator machines): `add [x]` — one implicit register.
- **0-address** (stack machines): `add` — both operands and the result
  are implicit: pop two, push one.

The 0-address property is the whole game. Consequences, good and bad:

- **Code density.** No operand fields on arithmetic ops. A Setun-70
  operation is one 6-trit tryte; a full three-operand RISC instruction
  needs 32 bits. This mattered enormously when memory was drum-backed
  and it matters again wherever code travels (bytecode, embedded).
- **Trivial compilation of expressions.** Any expression tree flattens
  to postfix by a single post-order walk — no register allocation, no
  spilling, no graph coloring. This is why *virtual* machines (JVM,
  CPython, WASM, Forth inner interpreters, PostScript) are almost all
  stack machines: the compiler's back end nearly disappears.
- **Cheap interrupts and calls.** Nothing to save: the machine state
  *is* the stack pointers. Koopman documents interrupt latencies of a
  few cycles on hardware stack machines versus dozens on register
  machines of the same era.
- **The cost:** no random access to intermediates. Reusing a value that
  isn't on top requires stack manipulation (`dup`, `swap`, `rot`, deep
  picks) or a round-trip through memory. Register machines win on raw
  ILP because independent operands can be read in parallel; a pure
  stack serializes through its top. This is the standard argument for
  why hardware stack machines lost the general-purpose war in the
  1980s–90s — and why they persist in niches (real-time, embedded,
  VMs) where density and predictability beat ILP.

## 2. Formal footing

Two theory results frame everything:

1. **A single stack is not enough.** A finite control plus one unbounded
   stack is a pushdown automaton (PDA), which recognizes exactly the
   context-free languages — strictly weaker than a Turing machine.
2. **Two stacks are.** Two unbounded stacks simulate a Turing machine
   tape (left-of-head on one stack, right-of-head on the other; moving
   the head pops one and pushes the other). So a two-stack machine is
   Turing-complete with no other memory at all.

Real stack machines are Turing-complete for the mundane reason that
they also have random-access memory. But the two-stack result is a
pleasing echo: the Setun-70's *operand stack + return stack* pair is
the minimal architecture that is computationally universal in its own
right.

## 3. The two-stack (canonical) stack machine

Splitting the single stack in two is the load-bearing design decision,
and essentially every serious stack machine makes it:

- The **operand/data stack** holds expression intermediates.
- The **return stack** holds return addresses (and, in Forth practice,
  loop counters and temporaries).

Why split? Because the two uses have different lifetimes and shapes. A
subroutine call arriving mid-expression must not bury the operands the
expression still needs. With separate stacks, a called word can consume
and produce operands *for its caller* — the basis of concatenative
composition (§4) — while control flow threads through the other stack.
Koopman's taxonomy calls the classic design **MS0/SS0**: multiple
stacks, 0-operand. Examples: Forth's virtual machine, the Novix
NC4016 / Harris RTX2000 chips, and the **Setun-70 (1970)** — which
predates Forth's publication and arrived at the same architecture
independently (Brusentsov called the style "procedure-oriented"; his
group's follow-on DSSP language is a de-facto Forth dialect,
discovered convergently).

Hardware realizations differ on where the stack lives:

- **In main memory** with a pointer register — simple, slow (every op
  touches memory).
- **On-chip stack cache** of the top few elements with automatic
  spill/refill — the RTX2000 kept the top 4 in registers; studies in
  Koopman show a 4–8 element buffer captures >90% of stack traffic.
- **Hybrid**: top-of-stack (and often the second element) in dedicated
  latches, the rest in a memory page behind a pointer. This is exactly
  the Setun-70: `T` and `S` are register-resident, the rest of the
  operand stack sits in a RAM page addressed by `ph:pa` — which is why
  ops S10–S12 (`CP`/`EXP`/`LP`) exist to read and swap that pointer,
  and why our emulator's abstract `List[Word]` is an approximation to
  revisit in phase 2 (see `docs/fp-research/functional-stack.md`).

## 4. The software view: concatenative composition

Stack machine code has an algebra. Read each instruction as a function
from stacks to stacks; a program is the *composition* of its
instructions in sequence. Languages built on this (Forth, PostScript,
Joy, Factor, DSSP) are called **concatenative**: concatenating two
program texts composes their functions. Two practical consequences:

- **Stack effect notation** documents each word as `( before — after )`,
  e.g. `dup ( a — a a )`, Setun-70's `S+T ( t s — s+t )` with the
  spec's `↓` being the net depth change. Effects compose by matching:
  a sequence type-checks if each word's inputs are produced by its
  predecessors. Static "stack checkers" (and WASM's validation pass)
  are exactly this arithmetic; it would make a fine munit property for
  our assembler — declare effects per opcode, verify programs never
  underflow by static analysis and then confirm dynamically.
- **Factoring.** Because any contiguous instruction span with a clean
  net effect can be pulled out as a named subroutine, stack code
  refactors at zero cost — the origin of Forth's tiny-word style, and
  of Brusentsov's claim (in our Setun chapter PDF) that structured
  programming on the Setun-70 cut program-creation difficulty "five to
  seven times."

The recurring pain point is equally well documented: values needed out
of order force stack-shuffling noise (`swap rot over`), and deep stack
juggling is the concatenative equivalent of goto spaghetti. Modern
practice (Factor) mitigates with local bindings; hardware (Setun-70)
mitigates with the senior-tryte `t` convention and memory references.

## 5. Design lessons for this project

1. **Keep `T`/`S` privileged.** The hardware hybrid (top elements in
   registers, tail in memory) is both historically faithful and the
   right emulator structure for phase 2: model the stack page + `ph:pa`
   for real, but keep the pure-`step` shape.
2. **Stack effects as metadata.** Annotating `BasicOp`/`SpecialOp` with
   `(consumes, produces, drop)` triples would let the assembler reject
   underflowing programs at assembly time — cheap static analysis the
   real machine never had.
3. **The return stack is phase 2's heart.** Macro-operation linkage
   (`CMC`/`RMC`/`LMC`, the `cb`/`cc` registers) is the Setun-70's
   return-stack discipline; implementing it faithfully is what turns
   the emulator from a calculator into a computer.
4. **For tritium later:** the two-stack MS0 design is minimal and
   universal, but the ILP critique (§1) is the reason to consider
   departures — e.g. a register-window or belt hybrid over balanced
   ternary. Worth a dedicated note when tritium's design starts.

## References

- P. Koopman, *Stack Computers: The New Wave*, Ellis Horwood, 1989.
  (Free online; the taxonomy and hardware survey above.)
- C. Bailey & R. Sotudeh, various papers on stack caching depth.
- Hopcroft & Ullman, *Introduction to Automata Theory* — PDA vs
  two-stack equivalence results (§2).
- Brusentsov & Ramil Alvarez, *Ternary Computers: The Setun and the
  Setun 70* (`docs/setun-70/978-3-642-22816-2_10_Chapter.pdf`).
- `docs/setun-70/instruction-set.md` — the Setun-70's concrete op set.
- `docs/fp-research/functional-stack.md` — persistent stacks inside the
  emulator's pure step function.
