package setun.machine

import setun.core.*
import Trit.{N, Z, P}

/** The Setun-70 interpreter: a pure fetch/decode/execute step over
  * MachineState, per docs/setun-70/instruction-set.md.
  *
  * Phase 1 executes all basic operations (B1–B27), operand references,
  * and the register-only specials (CP, EXP, LP, LH1–LH3). Macro-calls,
  * external memory paging, and I/O channels fault as Unimplemented.
  * The 36-trit pair ops (LST, LBT, L*T, LHT) compute via Long values —
  * exact, since a 36-trit pair's range fits in a Long.
  */
object Machine:

  def step(m: MachineState): MachineState =
    if m.fault.isDefined then m
    else
      m.memory.tryte(m.cPage, m.cOffset) match
        case Left(f)  => m.fail(f)
        case Right(k) => exec(m, Instruction.decode(k))

  /** Run until a fault or `maxSteps` executed instructions. */
  def run(m: MachineState, maxSteps: Int): MachineState =
    var cur = m
    var i = 0
    while i < maxSteps && cur.fault.isEmpty do
      cur = step(cur)
      i += 1
    cur

  /** Every state from `m` through `maxSteps` steps — time-travel history. */
  def trace(m: MachineState, maxSteps: Int): LazyList[MachineState] =
    LazyList.iterate(m)(step).take(maxSteps + 1)

  private val P3_18 = 387420489L
  private val Max36 = (BigInt(3).pow(36).toLong - 1) / 2
  private val CotThreshold = (BigInt(3).pow(17).toLong - 1) / 2 // |S| > 3^17/2

  private def exec(m: MachineState, instr: Instruction): MachineState = instr match
    case Instruction.OperandRef(len, hReg, addrTrits) =>
      val page = m.h(hReg)
      val addr = Trits.toLong(addrTrits).toInt
      m.memory.read(page, addr, len) match
        case Left(f) => m.fail(f)
        case Right(trytes) =>
          val value = Trits.toLong(trytes.flatMap(_.trits))
          m.copy(operands = Word.fromLong(value) :: m.operands).advanced

    case Instruction.MacroCall(_) =>
      if m.c1 != N then m.fail(Fault.IllegalInMode("macro-operation"))
      else m.fail(Fault.Unimplemented("macro-operation")) // phase 2

    case Instruction.Basic(op) => basic(m, op)

    case Instruction.Special(op) =>
      if m.c1 == N then m.fail(Fault.IllegalInMode(op.mnemonic))
      else special(m, op)

  // -- basic operations ------------------------------------------------

  private def basic(m: MachineState, op: BasicOp): MachineState =
    import BasicOp.*
    op match
      case LST => // shift the pair S:Y by t digits; ↓
        m.operands match
          case t :: s :: rest =>
            val (s2, y2) = shiftPair(s, m.y, seniorTryte(t).toInt)
            m.copy(operands = s2 :: rest, y = y2).advanced
          case _ => m.fail(Fault.StackUnderflow)

      case COT => condJump(m, s => math.abs(s) > CotThreshold)
      case CLT => condJump(m, _ < 0)
      case CET => condJump(m, _ == 0)
      case CGT => condJump(m, _ > 0)

      case XNN => // normalize mantissa T:Y, exponent e
        m.operands match
          case t :: rest =>
            normalize(m, t) match
              case Left(f)             => m.fail(f)
              case Right((t2, y2, e2)) => m.copy(operands = t2 :: rest, y = y2, e = e2).advanced
          case _ => m.fail(Fault.StackUnderflow)

      case EDec => overflowChecked(m)(m.copy(e = m.e - Tryte.fromInt(1)).advanced)
      case EClr => m.copy(e = Tryte.Zero).advanced
      case EInc => overflowChecked(m)(m.copy(e = m.e + Tryte.fromInt(1)).advanced)

      case ESetT => // e := t; ↓
        m.operands match
          case t :: rest => m.copy(e = seniorTryte(t), operands = rest).advanced
          case _         => m.fail(Fault.StackUnderflow)

      case TSubE => tPlusMinusE(m, negateE = true)
      case TAddE => tPlusMinusE(m, negateE = false)

      case TSetC => // T := 0 with t := address of the current instruction
        m.operands match
          case _ :: rest =>
            val addr = Word.fromTrytes(Tryte.fromInt(m.cOffset), Tryte.Zero, Tryte.Zero)
            m.copy(operands = addr :: rest).advanced
          case _ => m.fail(Fault.StackUnderflow)

      case RSetT => popInto(m)((m2, t) => m2.copy(r = t))
      case YSetT => popInto(m)((m2, t) => m2.copy(y = t))
      case YClr  => popInto(m)((m2, _) => m2.copy(y = Word.Zero))
      case TDN =>
        m.operands match
          case t :: rest => m.copy(operands = (-t) :: rest).advanced
          case _         => m.fail(Fault.StackUnderflow)

      case CSetT => // unconditional in-page jump by offset in t; ↓
        m.operands match
          case t :: rest => jump(m.copy(operands = rest), t)
          case _         => m.fail(Fault.StackUnderflow)

      case TSetW => // T := memory operand named by the reference in t
        m.operands match
          case t :: rest =>
            withReference(m, t) { (len, page, addr) =>
              m.memory.read(page, addr, len) match
                case Left(f) => m.fail(f)
                case Right(trytes) =>
                  val w = Word.fromLong(Trits.toLong(trytes.flatMap(_.trits)))
                  m.copy(operands = w :: rest).advanced
            }
          case _ => m.fail(Fault.StackUnderflow)

      case WSetS => // memory[reference in t] := low trytes of S; ↓
        m.operands match
          case t :: s :: rest =>
            withReference(m, t) { (len, page, addr) =>
              val (t1, t2, t3) = s.trytes
              val data = Vector(t1, t2, t3).takeRight(len)
              m.memory.write(page, addr, data) match
                case Left(f)    => m.fail(f)
                case Right(mem) => m.copy(operands = s :: rest, memory = mem).advanced
            }
          case _ => m.fail(Fault.StackUnderflow)

      case SMT => tritwise(m)((a, b) => a * b)
      case SAT => tritwise(m)((a, b) => Trit.fromValue(Integer.signum(a.value + b.value)))

      case SSubT => binop(m)((s, t) => s - t)
      case SAddT => binop(m)((s, t) => s + t)

      case LBT => pairOp(m)((t, s, m2) => Right((s.toLong * P3_18 + t.toLong * m2.r.toLong * 9, m2)))
      case LMulT =>
        pairOp(m)((t, s, m2) => Right((t.toLong * s.toLong * 9, m2.copy(r = s))))
      case LHT =>
        pairOp(m)((t, s, m2) => Right((s.toLong * P3_18 + m2.y.toLong + t.toLong * m2.r.toLong * 9, m2)))

  // -- special operations ----------------------------------------------

  private def special(m: MachineState, op: SpecialOp): MachineState =
    import SpecialOp.*
    op match
      case CP => // push a word carrying the stack pointer: z[5:6] := ph, z[9:11] := pa
        val z = Trits.zero(Word.Width)
          .patch(4, m.ph, 2)
          .patch(8, m.pa, 3)
        m.copy(operands = Word(z) :: m.operands).advanced

      case EXP => // exchange current and reserve stack pointers
        m.copy(ph = m.reserve._1, pa = m.reserve._2, reserve = (m.ph, m.pa)).advanced

      case LP => // load the stack pointer from T
        m.operands match
          case t :: _ =>
            m.copy(ph = t.trits.slice(4, 6), pa = t.trits.slice(8, 11)).advanced
          case _ => m.fail(Fault.StackUnderflow)

      case LH1 => loadH(m, N)
      case LH2 => loadH(m, Z)
      case LH3 => loadH(m, P)

      case other => m.fail(Fault.Unimplemented(other.mnemonic)) // paging, I/O, macro linkage: phase 2

  // -- helpers -----------------------------------------------------------

  /** The spec's `t`: the first (most significant) tryte of T. */
  private def seniorTryte(w: Word): Tryte = w.trytes._1

  private def jump(m: MachineState, t: Word): MachineState =
    val offset = seniorTryte(t).toInt // ca := t
    if offset < Memory.MinAddr || offset > Memory.MaxAddr then m.fail(Fault.BadJump(offset))
    else m.copy(cOffset = offset)

  /** Conditional jump ops: test S, jump by the offset in t, always ↓. */
  private def condJump(m: MachineState, pred: Long => Boolean): MachineState =
    m.operands match
      case t :: s :: rest =>
        val m2 = m.copy(operands = s :: rest)
        if pred(s.toLong) then jump(m2, t) else m2.advanced
      case _ => m.fail(Fault.StackUnderflow)

  private def popInto(m: MachineState)(f: (MachineState, Word) => MachineState): MachineState =
    m.operands match
      case t :: rest => f(m.copy(operands = rest), t).advanced
      case _         => m.fail(Fault.StackUnderflow)

  /** S := f(S, T); ↓ — with word overflow becoming a fault. */
  private def binop(m: MachineState)(f: (Word, Word) => Word): MachineState =
    m.operands match
      case t :: s :: rest => overflowChecked(m)(m.copy(operands = f(s, t) :: rest).advanced)
      case _              => m.fail(Fault.StackUnderflow)

  /** S := T ⊙ S applied trit by trit; ↓ */
  private def tritwise(m: MachineState)(f: (Trit, Trit) => Trit): MachineState =
    m.operands match
      case t :: s :: rest =>
        val combined = Word(t.trits.lazyZip(s.trits).map(f))
        m.copy(operands = combined :: rest).advanced
      case _ => m.fail(Fault.StackUnderflow)

  /** T := T ± e×3^12 (e placed in the senior tryte position). */
  private def tPlusMinusE(m: MachineState, negateE: Boolean): MachineState =
    m.operands match
      case t :: rest =>
        val eWord = Word.fromTrytes(if negateE then -m.e else m.e, Tryte.Zero, Tryte.Zero)
        overflowChecked(m)(m.copy(operands = (t + eWord) :: rest).advanced)
      case _ => m.fail(Fault.StackUnderflow)

  /** The multiply-accumulate family: S:Y := value(T, S, state); ↓.
    * A 36-trit pair fits in a Long, so the value is computed exactly.
    */
  private def pairOp(m: MachineState)(
      f: (Word, Word, MachineState) => Either[Fault, (Long, MachineState)]
  ): MachineState =
    m.operands match
      case t :: s :: rest =>
        f(t, s, m) match
          case Left(fault) => m.fail(fault)
          case Right((value, m2)) =>
            if math.abs(value) > Max36 then m.fail(Fault.Overflow)
            else
              val pair = Trits.fromLong(value, 2 * Word.Width)
              m2.copy(
                operands = Word(pair.take(Word.Width)) :: rest,
                y = Word(pair.drop(Word.Width)),
              ).advanced
      case _ => m.fail(Fault.StackUnderflow)

  /** Shift the 36-trit pair (hi, lo) left (n > 0) or right (n < 0). */
  private def shiftPair(hi: Word, lo: Word, n: Int): (Word, Word) =
    val pair = hi.trits ++ lo.trits
    val k = math.min(math.abs(n), pair.length)
    val shifted =
      if n >= 0 then pair.drop(k) ++ Trits.zero(k)
      else Trits.zero(k) ++ pair.dropRight(k)
    (Word(shifted.take(Word.Width)), Word(shifted.drop(Word.Width)))

  /** Normalize (T:Y, e): shift the pair left until T's senior trit is
    * nonzero, decrementing e per shift; a zero mantissa clears e.
    */
  private def normalize(m: MachineState, t: Word): Either[Fault, (Word, Word, Tryte)] =
    var pair = t.trits ++ m.y.trits
    if pair.forall(_ == Z) then Right((Word.Zero, Word.Zero, Tryte.Zero))
    else
      var eVal = m.e.toInt
      while pair.head == Z do
        pair = pair.tail :+ Z
        eVal -= 1
      if eVal < Tryte.MinValue then Left(Fault.Overflow)
      else Right((Word(pair.take(Word.Width)), Word(pair.drop(Word.Width)), Tryte.fromInt(eVal)))

  /** Decode the operand reference held in the senior tryte of `t` (for
    * T=W / W=S) and hand (length, page, addr) to `f`.
    */
  private def withReference(m: MachineState, t: Word)(
      f: (Int, Int, Int) => MachineState
  ): MachineState =
    Instruction.decode(seniorTryte(t)) match
      case Instruction.OperandRef(len, hReg, addrTrits) =>
        f(len, m.h(hReg), Trits.toLong(addrTrits).toInt)
      case _ => m.fail(Fault.BadReference)

  /** Load page register h[reg] from z[4:6] of the stack top; ↓ */
  private def loadH(m: MachineState, reg: Trit): MachineState =
    m.operands match
      case t :: rest =>
        val page = Trits.toLong(t.trits.slice(3, 6)).toInt
        if !Memory.validPage(page) then m.fail(Fault.PageFault)
        else m.copy(h = m.h.updated(reg, page), operands = rest).advanced
      case _ => m.fail(Fault.StackUnderflow)

  private def overflowChecked(m: MachineState)(body: => MachineState): MachineState =
    try body
    catch case _: ArithmeticException => m.fail(Fault.Overflow)
