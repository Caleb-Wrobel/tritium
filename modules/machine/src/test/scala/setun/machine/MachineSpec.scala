package setun.machine

import setun.core.*
import setun.core.dsl.*
import setun.machine.asm.Asm
import Asm.*
import Trit.{N, Z, P}

class MachineSpec extends munit.FunSuite:

  val ProgPage = 5 // RAM, so W=S tests can write to it

  /** A word carrying an operand-reference tryte in the senior position —
    * the machine reads T=W / W=S references from t, the senior tryte of T.
    */
  def refWord(len: Int, addr: Int): Word =
    Word.fromTrytes(Asm.ref(len, Z, addr), Tryte.Zero, Tryte.Zero)

  test("operand refs push, S+T adds"):
    val prog = Asm.page(ProgPage):
      push(const(5))
      push(const(7))
      op(BasicOp.SAddT)
    val out = Machine.run(prog.boot, 3)
    assertEquals(out.fault, None)
    assertEquals(out.operands, List(12L.bw))

  test("RPN program: (a + b) × c via S+T, L*T, LST"):
    // L*T leaves T×R×3² in the pair S:Y; shifting the pair left by 16
    // brings the product into S (the paper-authentic scaling dance).
    val prog = Asm.page(ProgPage):
      push(const(5))
      push(const(7))
      op(BasicOp.SAddT)
      push(const(3))
      op(BasicOp.LMulT)
      push(constSenior(16))
      op(BasicOp.LST)
    val out = Machine.run(prog.boot, 7)
    assertEquals(out.fault, None)
    assertEquals(out.operands.head.toLong, 36L)
    assertEquals(out.r.toLong, 12L)
    assertEquals(out.y, Word.Zero)

  test("CGT jumps on S > 0 and sets ca := t"):
    def prog(s: Int) = Asm.page(ProgPage):
      push(const(s))
      jumpIfPos("end")
      op(BasicOp.TDN)
      label("end")

    val taken = prog(30)
    val out = Machine.run(taken.boot, 3)
    assertEquals(out.fault, None)
    assertEquals(out.cOffset, taken.labels("end"))
    assertEquals(out.operands, List(30L.bw))

    val notTaken = prog(-30)
    val out2 = Machine.run(notTaken.boot, 3)
    assertEquals(out2.cOffset, notTaken.labels("end") - 1) // fell through to TDN
    assertEquals(out2.operands, List((-30L).bw))

  test("T=W loads through a reference in t"):
    val prog = Asm.page(ProgPage):
      op(BasicOp.TSetW)
      data(20, Seq(9.bt))
    val m = prog.boot.copy(operands = List(refWord(1, 20)))
    val out = Machine.step(m)
    assertEquals(out.fault, None)
    assertEquals(out.operands, List(9L.bw))

  test("W=S writes S's low trytes through a reference in t"):
    val prog = Asm.page(ProgPage) { op(BasicOp.WSetS) }
    val m = prog.boot.copy(operands = List(refWord(1, 20), 42L.bw))
    val out = Machine.step(m)
    assertEquals(out.fault, None)
    assertEquals(out.operands, List(42L.bw))
    assertEquals(out.memory.tryte(ProgPage, 20), Right(42.bt))

  test("exponent register ops"):
    val prog = Asm.page(ProgPage):
      op(BasicOp.EInc); op(BasicOp.EInc); op(BasicOp.EDec)
    assertEquals(Machine.run(prog.boot, 3).e, 1.bt)

  test("TDN negates in place"):
    val prog = Asm.page(ProgPage) { op(BasicOp.TDN) }
    val m = prog.boot.copy(operands = List(5L.bw))
    assertEquals(Machine.step(m).operands, List((-5L).bw))

  test("faults: underflow, mode violation, page overrun, empty channel"):
    val underflow = Asm.page(ProgPage) { op(BasicOp.SAddT) }
    assertEquals(Machine.step(underflow.boot).fault, Some(Fault.StackUnderflow))

    val userMode = Asm.page(ProgPage) { op(SpecialOp.CP) }.boot.copy(c1 = N)
    assertEquals(Machine.step(userMode).fault, Some(Fault.IllegalInMode("CP")))

    val pastEdge = Asm.page(ProgPage) { raw(Asm.ref(3, Z, 39)) }
    assertEquals(Machine.step(pastEdge.boot).fault, Some(Fault.PageFault))

    val cg1 = Asm.page(ProgPage) { op(SpecialOp.CG1) }
    assertEquals(Machine.step(cg1.boot).fault, Some(Fault.ChannelEmpty(N)))

  test("CP/EXP/LP move the stack pointer registers"):
    val prog = Asm.page(ProgPage):
      op(SpecialOp.CP); op(SpecialOp.EXP); op(SpecialOp.LP)
    val m = prog.boot.copy(ph = Vector(Z, P), pa = Vector(P, Z, N))
    val afterCp = Machine.step(m)
    assertEquals(afterCp.fault, None)
    val pushed = afterCp.operands.head
    assertEquals(pushed.trits.slice(4, 6), Vector(Z, P))
    assertEquals(pushed.trits.slice(8, 11), Vector(P, Z, N))

    val afterExp = Machine.step(afterCp) // pointers swap with zeroed reserve
    assertEquals(afterExp.ph, Vector(Z, Z))
    assertEquals(afterExp.reserve, (Vector(Z, P), Vector(P, Z, N)))

    val afterLp = Machine.step(afterExp) // reload from the word CP pushed
    assertEquals(afterLp.fault, None)
    assertEquals(afterLp.ph, Vector(Z, P))
    assertEquals(afterLp.pa, Vector(P, Z, N))

  test("LH2 loads a page register from the stack top"):
    val prog = Asm.page(ProgPage) { op(SpecialOp.LH2) }
    val m = prog.boot.copy(
      operands = List(Word(Trits.zero(15).patch(3, Trits.fromLong(-13, 3), 3) ++ Trits.zero(3)))
    )
    val out = Machine.step(m)
    assertEquals(out.fault, None)
    assertEquals(out.h(Z), -13)
    assertEquals(out.operands, Nil)

  /** cb-layout linkage trits: mode at z[3], page at z[4:6], addr at z[9:12]. */
  def linkage(mode: Trit, page: Int, addr: Int): Vector[Trit] =
    Trits.zero(2) ++ (mode +: Trits.fromLong(page, 3)) ++
      Trits.zero(2) ++ Trits.fromLong(addr, 4)

  test("macro-operation calls through page −13 and RMC returns"):
    val sys = Asm.page(-13):
      data(-27, Seq(7.bt)) // dispatch parameter for macro 0 (ka = 0 − 27)
      org(-13)             // common handler entry m[−13,−13]
      op(BasicOp.TDN)      // negate the pushed parameter
      op(SpecialOp.RMC)
    val user = Asm.page(ProgPage):
      macroOp(0)
      op(BasicOp.EInc) // resumes here after RMC
    val mem = Memory(Map(-13 -> sys.page, ProgPage -> user.page))
    val m0 = MachineState.initial(mem).copy(c1 = N, cPage = ProgPage)

    val afterCall = Machine.step(m0)
    assertEquals(afterCall.fault, None)
    assertEquals(afterCall.c1, Z) // macro-operation mode
    assertEquals((afterCall.cPage, afterCall.cOffset), (-13, -13))
    assertEquals(afterCall.operands, List(Word.fromTrytes(7.bt, Tryte.Zero, Tryte.Zero)))
    assertEquals(afterCall.cb, linkage(N, ProgPage, -39)) // return past the call

    val done = Machine.run(afterCall, 3) // TDN, RMC, then EInc back home
    assertEquals(done.fault, None)
    assertEquals(done.c1, N)
    assertEquals((done.cPage, done.cOffset), (ProgPage, -38))
    assertEquals(done.e, 1.bt)
    assertEquals(done.operands, List(Word.fromTrytes((-7).bt, Tryte.Zero, Tryte.Zero)))
    assertEquals(done.cb, Trits.zero(12)) // RMC swapped the original cb back

  test("macro-operations are forbidden outside user mode"):
    val prog = Asm.page(ProgPage) { macroOp(0) }
    val expected = Some(Fault.IllegalInMode("macro-operation"))
    assertEquals(Machine.step(prog.boot).fault, expected) // boot = system mode
    assertEquals(Machine.step(prog.boot.copy(c1 = Z)).fault, expected)

  test("macro call at the page edge faults instead of saving address 41"):
    val prog = Asm.page(ProgPage) { data(40, Seq(Instruction.encodeMacro(0))) }
    val m = prog.boot.copy(c1 = N, cOffset = 40)
    assertEquals(Machine.step(m).fault, Some(Fault.PcOverrun))

  test("system code enters user mode via LMC + RMC"):
    // the boot idiom: forge a linkage word, load it into cb, "return" into it
    val sys = Asm.page(-13):
      op(SpecialOp.LMC)
      op(SpecialOp.RMC)
    val m0 = MachineState
      .initial(sys.memory)
      .copy(operands = List(Word(linkage(N, ProgPage, 20) ++ Trits.zero(6))))
    val out = Machine.run(m0, 2)
    assertEquals(out.fault, None)
    assertEquals(out.c1, N)
    assertEquals((out.cPage, out.cOffset), (ProgPage, 20))
    assertEquals(out.operands, Nil) // LMC popped the linkage word

  test("CMC pushes cb and exchanges cb with cc"):
    val prog = Asm.page(ProgPage) { op(SpecialOp.CMC) }
    val cb0 = linkage(N, 3, -10)
    val cc0 = linkage(P, -2, 25)
    val out = Machine.step(prog.boot.copy(cb = cb0, cc = cc0))
    assertEquals(out.fault, None)
    assertEquals(out.operands, List(Word(cb0 ++ Trits.zero(6))))
    assertEquals((out.cb, out.cc), (cc0, cb0))

  /** A word naming memory page `n` in t's trits 4:6 (the LH/CF/LF field). */
  def pageWord(n: Int): Word =
    Word(Trits.zero(3) ++ Trits.fromLong(n, 3) ++ Trits.zero(12))

  test("LQ/LF/CF round-trip a page through the drum"):
    val DataPage = 6
    val prog = Asm.page(ProgPage):
      op(SpecialOp.LQ2); op(SpecialOp.LF2); op(SpecialOp.CF2)
    val marker = 42.bt
    val Right(mem) = prog.memory.write(DataPage, 20, Vector(marker)): @unchecked
    val m0 = prog.boot.copy(memory = mem, operands = List(pageWord(DataPage)))

    val afterLq = Machine.step(m0) // q[0] from T[5:12]; T stays
    assertEquals(afterLq.fault, None)
    assertEquals(afterLq.operands.length, 1)

    val afterLf = Machine.step(afterLq) // page 6 unloaded to f[0, q[0]]; T stays
    assertEquals(afterLf.fault, None)
    assertEquals(afterLf.drums(Z)(afterLf.q(Z))(20 + 40), marker)

    // tamper with RAM, then CF must restore the drum copy
    val Right(blanked) = afterLf.memory.write(DataPage, 20, Vector(Tryte.Zero)): @unchecked
    val afterCf = Machine.step(afterLf.copy(memory = blanked))
    assertEquals(afterCf.fault, None)
    assertEquals(afterCf.memory.tryte(DataPage, 20), Right(marker))
    assertEquals(afterCf.operands, Nil) // CF pops

  test("CF into a ROM page faults"):
    val prog = Asm.page(ProgPage) { op(SpecialOp.CF2) }
    val m = prog.boot.copy(operands = List(pageWord(0))) // page 0 is ROM
    assertEquals(Machine.step(m).fault, Some(Fault.ReadOnlyPage))

  test("CG pushes channel input with the bit-7 sign convention"):
    val prog = Asm.page(ProgPage) { op(SpecialOp.CG2); op(SpecialOp.CG2) }
    val ch = Channel(input = List(64 | 42, 42)) // bit 7 set → plus, clear → minus
    val m = prog.boot.copy(channels = prog.boot.channels.updated(Z, ch))
    val out = Machine.run(m, 2)
    assertEquals(out.fault, None)
    val senior = (v: Int) => Word.fromTrytes(v.bt, Tryte.Zero, Tryte.Zero)
    assertEquals(out.operands, List(senior(-42), senior(42)))
    assertEquals(out.channels(Z).input, Nil)

  test("LU activates a channel and LG logs output; both pop"):
    val prog = Asm.page(ProgPage) { op(SpecialOp.LU3); op(SpecialOp.LG3) }
    val ctrl = Word.fromTrytes(7.bt, Tryte.Zero, Tryte.Zero)
    val m = prog.boot.copy(operands = List(ctrl, 99L.bw))
    val out = Machine.run(m, 2)
    assertEquals(out.fault, None)
    assertEquals(out.operands, Nil)
    assertEquals(out.channels(P).control, 7.bt)
    assertEquals(out.channels(P).active, true)
    assertEquals(out.channels(P).output, Vector(99L.bw))

  test("trace returns the full step history"):
    val prog = Asm.page(ProgPage):
      push(const(5)); push(const(7)); op(BasicOp.SAddT)
    val history = Machine.trace(prog.boot, 3).toList
    assertEquals(history.length, 4)
    assertEquals(history.head, prog.boot)
    assertEquals(history.map(_.operands.length), List(0, 1, 2, 1))
