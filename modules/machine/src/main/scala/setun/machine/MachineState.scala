package setun.machine

import setun.core.*
import Trit.{N, Z, P}

enum Fault:
  case StackUnderflow
  case PageFault                    // the spec's w = −29 exception
  case ReadOnlyPage
  case Overflow
  case BadJump(offset: Int)
  case BadReference
  case PcOverrun
  case IllegalInMode(what: String)
  case Unimplemented(what: String)

/** Complete Setun-70 machine state. Immutable: `Machine.step` is a pure
  * function `MachineState => MachineState`, so any state is a free
  * snapshot (see docs/fp-research/functional-stack.md).
  *
  * Notation follows docs/setun-70/instruction-set.md: the operand stack's
  * `T` is `operands.head`, `S` is the sub-top, and the spec's `↓` is a
  * one-word drop. The hardware keeps this stack in a memory page behind
  * the `ph:pa` pointer; phase 1 models the stack abstractly and carries
  * `ph`/`pa` as plain register values (ops S10–S12 move them around
  * without governing the list).
  */
final case class MachineState(
    operands: List[Word],           // T = head, S = second
    e: Tryte,                       // exponent register
    r: Word,                        // multiplier register R
    y: Word,                        // extension register Y (low half of S:Y, T:Y pairs)
    c1: Trit,                       // mode: N user, Z macro-operation, P system
    cPage: Int,                     // instruction pointer: page −13…+13
    cOffset: Int,                   // instruction pointer: in-page address −40…+40
    cb: Vector[Trit],               // macro-call linkage, spec's c[9:20] (12 trits)
    cc: Vector[Trit],               // macro-call linkage, spec's c[21:32] (12 trits)
    ph: Vector[Trit],               // stack pointer, page half (2 trits)
    pa: Vector[Trit],               // stack pointer, address half (3 trits)
    reserve: (Vector[Trit], Vector[Trit]), // reserve stack pointer (EXP swaps)
    h: Map[Trit, Int],              // page registers h[-1], h[0], h[+1] → page index
    memory: Memory,
    fault: Option[Fault] = None,
):
  def fail(f: Fault): MachineState = copy(fault = Some(f))

  /** Move the instruction pointer to the next tryte. */
  def advanced: MachineState =
    if cOffset + 1 > Memory.MaxAddr then fail(Fault.PcOverrun)
    else copy(cOffset = cOffset + 1)

object MachineState:
  /** Power-on state: system mode, executing from the first tryte of the
    * lowest ROM page, page registers pointing at that page.
    */
  def initial(memory: Memory): MachineState = MachineState(
    operands = Nil,
    e = Tryte.Zero,
    r = Word.Zero,
    y = Word.Zero,
    c1 = P,
    cPage = -13,
    cOffset = Memory.MinAddr,
    cb = Trits.zero(12),
    cc = Trits.zero(12),
    ph = Vector(Z, Z),
    pa = Vector(Z, Z, Z),
    reserve = (Vector(Z, Z), Vector(Z, Z, Z)),
    h = Map(N -> -13, Z -> -13, P -> -13),
    memory = memory,
  )
