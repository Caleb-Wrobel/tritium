package setun.machine

import setun.core.*
import Trit.{N, Z, P}

/** The Setun-70 interpreter: a pure fetch/decode/execute step over
  * MachineState, per docs/setun-70/instruction-set.md.
  *
  * Executes the full instruction set: all basic operations (B1–B27),
  * operand references, all special operations (S1–S27), and the
  * macro-operation (system call) mechanism. The operand stack is
  * memory-backed behind ph:pa (see MachineState), so EXP and LP switch
  * between real stacks. Devices are modeled purely: drums are page
  * maps, I/O channels input queues and output logs a driver fills and
  * drains between steps.
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

  // -- stack plumbing ----------------------------------------------------
  // faults land on the original state `m`, so a faulted snapshot keeps
  // the registers exactly as they were when the op was attempted

  private def popped(m: MachineState)(f: (Word, MachineState) => MachineState): MachineState =
    m.pop.fold(m.fail, f.tupled)

  private def popped2(m: MachineState)(f: (Word, Word, MachineState) => MachineState): MachineState =
    m.pop2.fold(m.fail, f.tupled)

  private def peeked(m: MachineState)(f: Word => MachineState): MachineState =
    m.top.fold(m.fail, f)

  private def exec(m: MachineState, instr: Instruction): MachineState = instr match
    case Instruction.OperandRef(len, hReg, addrTrits) =>
      val page = m.h(hReg)
      val addr = Trits.toLong(addrTrits).toInt
      m.memory.read(page, addr, len) match
        case Left(f) => m.fail(f)
        case Right(trytes) =>
          val value = Word.fromLong(Trits.toLong(trytes.flatMap(_.trits)))
          m.push(value).fold(m.fail, _.advanced)

    case Instruction.MacroCall(ka) =>
      if m.c1 != N then m.fail(Fault.IllegalInMode("macro-operation"))
      else macroCall(m, ka)

    case Instruction.Basic(op) => basic(m, op)

    case Instruction.Special(op) =>
      if m.c1 == N then m.fail(Fault.IllegalInMode(op.mnemonic))
      else special(m, op)

  // -- basic operations ------------------------------------------------

  private def basic(m: MachineState, op: BasicOp): MachineState =
    import BasicOp.*
    op match
      case LST => // shift the pair S:Y by t digits; ↓
        popped2(m): (t, s, m2) =>
          val (s2, y2) = shiftPair(s, m.y, seniorTryte(t).toInt)
          m2.push(s2).fold(m.fail, _.copy(y = y2).advanced)

      case COT => condJump(m, s => math.abs(s) > CotThreshold)
      case CLT => condJump(m, _ < 0)
      case CET => condJump(m, _ == 0)
      case CGT => condJump(m, _ > 0)

      case XNN => // normalize mantissa T:Y, exponent e
        peeked(m): t =>
          normalize(m, t) match
            case Left(f) => m.fail(f)
            case Right((t2, y2, e2)) =>
              m.replaceTop(t2).fold(m.fail, _.copy(y = y2, e = e2).advanced)

      case EDec => overflowChecked(m)(m.copy(e = m.e - Tryte.fromInt(1)).advanced)
      case EClr => m.copy(e = Tryte.Zero).advanced
      case EInc => overflowChecked(m)(m.copy(e = m.e + Tryte.fromInt(1)).advanced)

      case ESetT => // e := t; ↓
        popped(m)((t, m2) => m2.copy(e = seniorTryte(t)).advanced)

      case TSubE => tPlusMinusE(m, negateE = true)
      case TAddE => tPlusMinusE(m, negateE = false)

      case TSetC => // T := 0 with t := address of the current instruction
        val addr = Word.fromTrytes(Tryte.fromInt(m.cOffset), Tryte.Zero, Tryte.Zero)
        m.replaceTop(addr).fold(m.fail, _.advanced)

      case RSetT => popped(m)((t, m2) => m2.copy(r = t).advanced)
      case YSetT => popped(m)((t, m2) => m2.copy(y = t).advanced)
      case YClr  => popped(m)((_, m2) => m2.copy(y = Word.Zero).advanced)

      case TDN =>
        peeked(m)(t => m.replaceTop(-t).fold(m.fail, _.advanced))

      case CSetT => // unconditional in-page jump by offset in t; ↓
        popped(m)((t, m2) => jump(m2, t))

      case TSetW => // T := memory operand named by the reference in t
        peeked(m): t =>
          withReference(m, t) { (len, page, addr) =>
            m.memory.read(page, addr, len) match
              case Left(f) => m.fail(f)
              case Right(trytes) =>
                val w = Word.fromLong(Trits.toLong(trytes.flatMap(_.trits)))
                m.replaceTop(w).fold(m.fail, _.advanced)
          }

      case WSetS => // memory[reference in t] := low trytes of S; ↓
        popped(m): (t, m2) =>
          m2.top match
            case Left(f) => m.fail(f)
            case Right(s) =>
              withReference(m, t) { (len, page, addr) =>
                val (t1, t2, t3) = s.trytes
                val data = Vector(t1, t2, t3).takeRight(len)
                m2.memory.write(page, addr, data) match
                  case Left(f)    => m.fail(f)
                  case Right(mem) => m2.copy(memory = mem).advanced
              }

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
        m.push(Word(z)).fold(m.fail, _.advanced)

      case EXP => // exchange current and reserve stack pointers
        m.copy(ph = m.reserve._1, pa = m.reserve._2, reserve = (m.ph, m.pa)).advanced

      case LP => // load the stack pointer from T (read from the old stack)
        peeked(m): t =>
          m.copy(ph = t.trits.slice(4, 6), pa = t.trits.slice(8, 11)).advanced

      case CMC => // copy cb to stack top; exchange cb and cc
        val z = Word(m.cb ++ Trits.zero(Word.Width - m.cb.length))
        m.push(z).fold(m.fail, _.copy(cb = m.cc, cc = m.cb).advanced)

      case RMC => // return from macro-operation: restore c from cb, swap cb/cc
        val (mode, page, addr) = unpackC(m.cb)
        m.copy(cb = m.cc, cc = m.cb, c1 = mode, cPage = page, cOffset = addr)

      case LMC => // cb := z[1:12] of T, old cb → cc; ↓
        popped(m)((t, m2) => m2.copy(cc = m.cb, cb = t.trits.take(12)).advanced)

      case LH1 => loadH(m, N)
      case LH2 => loadH(m, Z)
      case LH3 => loadH(m, P)

      case LQ1 => loadQ(m, N)
      case LQ2 => loadQ(m, Z)
      case LQ3 => loadQ(m, P)
      case CF1 => drumToRam(m, N)
      case CF2 => drumToRam(m, Z)
      case CF3 => drumToRam(m, P)
      case LF1 => ramToDrum(m, N)
      case LF2 => ramToDrum(m, Z)
      case LF3 => ramToDrum(m, P)

      case CG1 => readChannel(m, N)
      case CG2 => readChannel(m, Z)
      case CG3 => readChannel(m, P)
      case LU1 => loadU(m, N)
      case LU2 => loadU(m, Z)
      case LU3 => loadU(m, P)
      case LG1 => writeChannel(m, N)
      case LG2 => writeChannel(m, Z)
      case LG3 => writeChannel(m, P)

  // -- macro-operations --------------------------------------------------

  /** Page −13 holds the macro dispatch table (parameter trytes at
    * addresses ka = −40…−14) and the common handler entry at m[−13,−13].
    */
  private val MacroPage = -13
  private val MacroEntry = -13

  /** Macro-operation (system call): cc := cb, cb := c, push the dispatch
    * parameter m[−13, ka] into t (T's other trytes cleared), and enter
    * macro mode at the handler entry m[−13, −13].
    *
    * Spec deviations/interpretations (the PDF leaves both implicit):
    * the saved c is the address of the *next* instruction, so RMC
    * resumes after the call; and c[1:8] — packed into cb at z[3:6] and
    * z[9:12] per RMC's restore — is read as the mode trit c1, the
    * 3-trit page, then the 4-trit in-page address.
    */
  private def macroCall(m: MachineState, ka: Vector[Trit]): MachineState =
    if m.cOffset + 1 > Memory.MaxAddr then m.fail(Fault.PcOverrun)
    else
      m.memory.tryte(MacroPage, Trits.toLong(ka).toInt) match
        case Left(f) => m.fail(f)
        case Right(param) =>
          m.push(Word.fromTrytes(param, Tryte.Zero, Tryte.Zero)) match
            case Left(f) => m.fail(f)
            case Right(m2) =>
              m2.copy(
                cc = m.cb,
                cb = packC(m.c1, m.cPage, m.cOffset + 1),
                c1 = Z,
                cPage = MacroPage,
                cOffset = MacroEntry,
              )

  /** Pack an instruction-pointer snapshot c[1:8] into the 12-trit cb
    * layout: c[1:4] at z[3:6], c[5:8] at z[9:12], rest zero.
    */
  private def packC(mode: Trit, page: Int, addr: Int): Vector[Trit] =
    Trits.zero(2) ++ (mode +: Trits.fromLong(page, 3)) ++
      Trits.zero(2) ++ Trits.fromLong(addr, 4)

  private def unpackC(z: Vector[Trit]): (Trit, Int, Int) =
    (z(2), Trits.toLong(z.slice(3, 6)).toInt, Trits.toLong(z.slice(8, 12)).toInt)

  // -- drum paging -------------------------------------------------------

  /** LQ: q[i] := T[5:12]. The hardware then starts an asynchronous page
    * search (SUIT); the pure model's drum access is immediate, so the
    * search is a no-op. T stays (the spec marks no ↓).
    */
  private def loadQ(m: MachineState, i: Trit): MachineState =
    peeked(m): t =>
      m.copy(q = m.q.updated(i, Trits.toLong(t.trits.slice(4, 12)).toInt)).advanced

  /** CF: copy external page f[i, q[i]] into the RAM page named in t; ↓.
    * The page number is read from t's trits 4:6 as in LH1–LH3; an
    * unwritten drum page reads as zeros, like unset operating memory.
    */
  private def drumToRam(m: MachineState, i: Trit): MachineState =
    popped(m): (t, m2) =>
      val page = Trits.toLong(t.trits.slice(3, 6)).toInt
      val data = m.drums(i).getOrElse(m.q(i), Memory.BlankPage)
      m2.memory.write(page, Memory.MinAddr, data) match
        case Left(f)    => m.fail(f)
        case Right(mem) => m2.copy(memory = mem).advanced

  /** LF: unload the page numbered in t (trits 4:6, as for CF) to
    * external memory f[i, q[i]]. T stays (the spec marks no ↓ here,
    * unlike CF).
    */
  private def ramToDrum(m: MachineState, i: Trit): MachineState =
    peeked(m): t =>
      val page = Trits.toLong(t.trits.slice(3, 6)).toInt
      m.memory.read(page, Memory.MinAddr, Memory.PageSize) match
        case Left(f) => m.fail(f)
        case Right(data) =>
          m.copy(drums = m.drums.updated(i, m.drums(i).updated(m.q(i), data))).advanced

  // -- I/O channels ------------------------------------------------------

  /** CG: read one pending value from channel i to the stack top:
    * T := 0, t := ±g[i,1:6], plus when bit 7 is set. Interpreted as a
    * push (as CP's "to stack top" is); consuming an empty channel
    * faults — the hardware would wait on the device instead.
    */
  private def readChannel(m: MachineState, i: Trit): MachineState =
    val ch = m.channels(i)
    ch.input match
      case v :: rest =>
        val data = if ((v >> 6) & 1) == 1 then v & 63 else -(v & 63)
        m.push(Word.fromTrytes(Tryte.fromInt(data), Tryte.Zero, Tryte.Zero)) match
          case Left(f)   => m.fail(f)
          case Right(m2) => m2.copy(channels = m.channels.updated(i, ch.copy(input = rest))).advanced
      case Nil => m.fail(Fault.ChannelEmpty(i))

  /** LU: load channel i's control register from t and mark it active; ↓ */
  private def loadU(m: MachineState, i: Trit): MachineState =
    popped(m): (t, m2) =>
      val ch = m.channels(i).copy(control = seniorTryte(t), active = true)
      m2.copy(channels = m.channels.updated(i, ch)).advanced

  /** LG: write T to channel i; ↓. The hardware's TRANSOUT is immediate
    * in the pure model: the word lands on the channel's output log.
    */
  private def writeChannel(m: MachineState, i: Trit): MachineState =
    popped(m): (t, m2) =>
      val ch = m.channels(i)
      m2.copy(channels = m.channels.updated(i, ch.copy(output = ch.output :+ t))).advanced

  // -- helpers -----------------------------------------------------------

  /** The spec's `t`: the first (most significant) tryte of T. */
  private def seniorTryte(w: Word): Tryte = w.trytes._1

  private def jump(m: MachineState, t: Word): MachineState =
    val offset = seniorTryte(t).toInt // ca := t
    if offset < Memory.MinAddr || offset > Memory.MaxAddr then m.fail(Fault.BadJump(offset))
    else m.copy(cOffset = offset)

  /** Conditional jump ops: test S, jump by the offset in t, always ↓. */
  private def condJump(m: MachineState, pred: Long => Boolean): MachineState =
    popped(m): (t, m2) =>
      m2.top match
        case Left(f)  => m.fail(f)
        case Right(s) => if pred(s.toLong) then jump(m2, t) else m2.advanced

  /** S := f(S, T); ↓ — with word overflow becoming a fault. */
  private def binop(m: MachineState)(f: (Word, Word) => Word): MachineState =
    popped2(m): (t, s, m2) =>
      overflowChecked(m)(m2.push(f(s, t)).fold(m.fail, _.advanced))

  /** S := T ⊙ S applied trit by trit; ↓ */
  private def tritwise(m: MachineState)(f: (Trit, Trit) => Trit): MachineState =
    popped2(m): (t, s, m2) =>
      val combined = Word(t.trits.lazyZip(s.trits).map(f))
      m2.push(combined).fold(m.fail, _.advanced)

  /** T := T ± e×3^12 (e placed in the senior tryte position). */
  private def tPlusMinusE(m: MachineState, negateE: Boolean): MachineState =
    peeked(m): t =>
      val eWord = Word.fromTrytes(if negateE then -m.e else m.e, Tryte.Zero, Tryte.Zero)
      overflowChecked(m)(m.replaceTop(t + eWord).fold(m.fail, _.advanced))

  /** The multiply-accumulate family: S:Y := value(T, S, state); ↓.
    * A 36-trit pair fits in a Long, so the value is computed exactly.
    */
  private def pairOp(m: MachineState)(
      f: (Word, Word, MachineState) => Either[Fault, (Long, MachineState)]
  ): MachineState =
    popped2(m): (t, s, m2) =>
      f(t, s, m2) match
        case Left(fault) => m.fail(fault)
        case Right((value, m3)) =>
          if math.abs(value) > Max36 then m.fail(Fault.Overflow)
          else
            val pair = Trits.fromLong(value, 2 * Word.Width)
            m3.push(Word(pair.take(Word.Width)))
              .fold(m.fail, _.copy(y = Word(pair.drop(Word.Width))).advanced)

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
    popped(m): (t, m2) =>
      val page = Trits.toLong(t.trits.slice(3, 6)).toInt
      if !Memory.validPage(page) then m.fail(Fault.PageFault)
      else m2.copy(h = m.h.updated(reg, page)).advanced

  private def overflowChecked(m: MachineState)(body: => MachineState): MachineState =
    try body
    catch case _: ArithmeticException => m.fail(Fault.Overflow)
