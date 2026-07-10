# The purely functional stack

Background note for the Tritium emulator's core design decision: the
Setun-70's two stacks (operand and return) will be immutable values inside
a pure `step: MachineState => MachineState` function.

## Background

The stack is the "hello world" of persistent data structures. A mutable
stack is an array plus a pointer; a *functional* stack is simply an
immutable singly linked list — a **cons list**:

```
Stack = Empty
      | Node(top, rest)     -- "cons cell": one value + the rest of the stack
```

Every operation builds a *new* stack value instead of mutating the old
one, yet nothing is ever copied:

- **push** allocates exactly one node whose `rest` pointer is the old
  stack. Cost: O(1).
- **pop** returns the `top` value and the existing `rest` — zero
  allocation. Cost: O(1).
- The old stack is untouched and still valid. Both versions **share**
  their common tail structurally.

This last property is called **persistence**: all previous versions of the
structure remain usable forever. "Copying the whole stack" to snapshot it
is free, because a snapshot is just a pointer. Chris Okasaki's *Purely
Functional Data Structures* (1998) opens with exactly this structure and
builds the rest of the field on top of it.

Persistence is precisely what an emulator wants. If each machine step is a
pure function from state to state, then keeping every intermediate state —
for time-travel debugging, tracing, or property tests that compare
execution paths — costs one O(1) snapshot per step, not one deep copy per
step.

## Pseudocode

```
type Stack[A] = Empty | Node(top: A, rest: Stack[A])

push(s: Stack[A], x: A) -> Stack[A]:
    return Node(x, s)                      -- O(1), one allocation

peek(s: Stack[A]) -> Option[A]:
    case Empty      -> None                -- O(1)
    case Node(x, _) -> Some(x)

pop(s: Stack[A]) -> Option[(A, Stack[A])]:
    case Empty      -> None                -- O(1), zero allocation
    case Node(x, r) -> Some((x, r))

depth(s: Stack[A]) -> Nat:
    case Empty      -> 0                   -- O(n); cache it in Node if
    case Node(_, r) -> 1 + depth(r)        -- the machine needs it often
```

### Structural sharing, worked example

```
s0 = Empty
s1 = push(s0, A)          -- A
s2 = push(s1, B)          -- B → A
s3 = push(s2, C)          -- C → B → A

(_, s4) = pop(s3)         -- s4 is literally s2: same pointer
s5 = push(s2, D)          -- D → B → A
```

Heap picture after all five bindings — one shared spine, no copies:

```
s3: C ─┐
       ├─→ B ─→ A ─→ ∅
s5: D ─┘   ↑
s2, s4 ────┘
```

`s3` and `s5` are two diverged futures of `s2`; both remain valid, and the
`B → A` tail exists exactly once in memory. This is the emulator's
time-travel property in miniature: `s2` is a snapshot we never had to copy.

## Mapping to Tritium

Scala's `List` *is* the cons list above (`::` is `Node`, `Nil` is
`Empty`), so the emulator needs no custom stack type:

```scala
final case class MachineState(
  operands: List[Word],   // T = operands.head, S = operands(1)
  returns:  List[Word],   // macro-call linkage stack
  // ... registers: e, R, Y, c, ph:pa, h[-1..+1], memory pages ...
)
```

Correspondence with the instruction-set spec
(`docs/setun-70/instruction-set.md`):

- `T` (top word) is `operands.head`; `S` (sub-top) is `operands(1)`.
- The `↓` stack-drop notation ("the old sub-top becomes the new top") is
  `operands.tail`.
- An operation like **B24 `S+T`** (`S := S + T; ↓`) becomes:

```scala
def sPlusT(m: MachineState): MachineState = m.operands match
  case t :: s :: rest => m.copy(operands = (s + t) :: rest)
  case _              => fault(m, StackUnderflow)
```

- Snapshots for time-travel debugging: `val history = state :: history` —
  O(1) per step, by everything above.

One caveat: the real Setun-70 operand stack lives in a memory page and has
a hardware pointer (`ph:pa`, see ops S10–S12 `CP`/`EXP`/`LP`, which read
and swap that pointer). A faithful emulator will eventually need to model
the stack *in memory* rather than as an abstract list; the plan is to
start with the abstract `List` for the arithmetic core and introduce the
memory-backed representation when the paging ops land. The pure-function
shape of `step` is unaffected by that swap.

## References

- Chris Okasaki, *Purely Functional Data Structures*, Cambridge University
  Press, 1998. (Chapter 2 covers exactly this stack.)
- `docs/setun-70/instruction-set.md` — stack notation (`T`, `S`, `↓`) and
  the stack-pointer ops S10–S12.
- Brusentsov & Ramil Alvarez, *Ternary Computers: The Setun and the Setun
  70* (`docs/setun-70/978-3-642-22816-2_10_Chapter.pdf`) — the two-stack
  architecture rationale.
