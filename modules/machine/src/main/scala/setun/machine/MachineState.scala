package setun.machine

import setun.core.*
import Trit.{N, Z, P}

enum Fault:
  case StackUnderflow
  case StackOverflow                // the operand stack filled its page
  case PageFault                    // the spec's w = −29 exception
  case ReadOnlyPage
  case Overflow
  case BadJump(offset: Int)
  case BadReference
  case PcOverrun
  case IllegalInMode(what: String)
  case ChannelEmpty(channel: Trit)  // CG consumed a channel with no pending input

/** One I/O channel's state — the spec's g (data), u (control) and a
  * (active) registers, modeled purely. `input` holds pending device
  * values that CG consumes (7-bit binary: bits 1–6 data, bit 7 the sign
  * flag); `output` logs the words TRANSOUT (LG) has written. A driver
  * loads `input` and drains `output` between steps.
  */
final case class Channel(
    input: List[Int] = Nil,
    output: Vector[Word] = Vector.empty,
    control: Tryte = Tryte.Zero,
    active: Boolean = false,
)

/** Complete Setun-70 machine state. Immutable: `Machine.step` is a pure
  * function `MachineState => MachineState`, so any state is a free
  * snapshot (see docs/fp-research/functional-stack.md).
  *
  * The operand stack lives in operating memory behind the ph:pa pointer,
  * as on the hardware. `ph` (2 trits) names RAM page ph + 9 — an
  * interpretation: the spec only sizes the field, and its nine values
  * must reach the nine RAM pages +5…+13. `pa` (3 trits) is the next
  * free word slot, −13…+13, slot k occupying trytes 3k−1…3k+1 of the
  * page. An empty stack is pa = −13. A push at pa = +13 faults
  * StackOverflow — the incremented pointer would not fit in three
  * trits, so a page holds at most 26 stack words (deviation: the spec
  * doesn't say how fullness is handled). The spec's T is the word just
  * below pa, S the one below that, and ↓ a one-word pointer drop —
  * popped words stay behind in memory.
  */
final case class MachineState(
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
    q: Map[Trit, Int],              // external page number registers q[i]
    drums: Map[Trit, Map[Int, Vector[Tryte]]], // external page memory f[i,·] (МБ1–МБ3)
    channels: Map[Trit, Channel],   // I/O channels 1–3 (indexed −1, 0, +1)
    fault: Option[Fault] = None,
):
  def fail(f: Fault): MachineState = copy(fault = Some(f))

  /** Move the instruction pointer to the next tryte. */
  def advanced: MachineState =
    if cOffset + 1 > Memory.MaxAddr then fail(Fault.PcOverrun)
    else copy(cOffset = cOffset + 1)

  // -- the memory-backed operand stack -----------------------------------

  /** The RAM page the current stack occupies. */
  def stackPage: Int = Trits.toLong(ph).toInt + 9

  private def slot: Int = Trits.toLong(pa).toInt

  private def wordAt(k: Int): Either[Fault, Word] =
    memory.read(stackPage, 3 * k - 1, 3).map(ts => Word.fromTrytes(ts(0), ts(1), ts(2)))

  def push(w: Word): Either[Fault, MachineState] =
    if slot == 13 then Left(Fault.StackOverflow)
    else
      val (a, b, c) = w.trytes
      memory
        .write(stackPage, 3 * slot - 1, Vector(a, b, c))
        .map(mem => copy(memory = mem, pa = Trits.fromLong(slot + 1, 3)))

  /** T without popping. */
  def top: Either[Fault, Word] =
    if slot == -13 then Left(Fault.StackUnderflow) else wordAt(slot - 1)

  def pop: Either[Fault, (Word, MachineState)] =
    top.map(w => (w, copy(pa = Trits.fromLong(slot - 1, 3))))

  /** Pop T then S: the argument order of the spec's binary ops. */
  def pop2: Either[Fault, (Word, Word, MachineState)] =
    pop.flatMap((t, m1) => m1.pop.map((s, m2) => (t, s, m2)))

  def replaceTop(w: Word): Either[Fault, MachineState] =
    pop.flatMap((_, m1) => m1.push(w))

  /** The current stack read out of memory, top (T) first — a debugging
    * and testing view; the machine itself goes through push/pop.
    */
  def operands: List[Word] =
    (1 to slot + 13).toList.map(i => wordAt(slot - i).getOrElse(Word.Zero))

  /** Replace the current stack's contents with `ws`, head becoming T —
    * a driver/test convenience.
    */
  def withStack(ws: List[Word]): MachineState =
    val mem = ws.reverse.zipWithIndex.foldLeft(memory) { case (acc, (w, i)) =>
      val (a, b, c) = w.trytes
      acc.write(stackPage, 3 * (i - 13) - 1, Vector(a, b, c)).getOrElse(acc)
    }
    copy(memory = mem, pa = Trits.fromLong(ws.length - 13, 3))

object MachineState:
  /** Power-on state: system mode, executing from the first tryte of the
    * lowest ROM page, page registers pointing at that page. The stack
    * pointers are an interpretation (boot software would set them): the
    * current stack empty on RAM page 9, the reserve empty on page 10.
    */
  def initial(memory: Memory): MachineState = MachineState(
    e = Tryte.Zero,
    r = Word.Zero,
    y = Word.Zero,
    c1 = P,
    cPage = -13,
    cOffset = Memory.MinAddr,
    cb = Trits.zero(12),
    cc = Trits.zero(12),
    ph = Vector(Z, Z),
    pa = Trits.fromLong(-13, 3),
    reserve = (Vector(Z, P), Trits.fromLong(-13, 3)),
    h = Map(N -> -13, Z -> -13, P -> -13),
    memory = memory,
    q = Map(N -> 0, Z -> 0, P -> 0),
    drums = Map(N -> Map.empty, Z -> Map.empty, P -> Map.empty),
    channels = Map(N -> Channel(), Z -> Channel(), P -> Channel()),
  )
