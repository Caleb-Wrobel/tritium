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

  test("faults: underflow, mode violation, page overrun, unimplemented"):
    val underflow = Asm.page(ProgPage) { op(BasicOp.SAddT) }
    assertEquals(Machine.step(underflow.boot).fault, Some(Fault.StackUnderflow))

    val userMode = Asm.page(ProgPage) { op(SpecialOp.CP) }.boot.copy(c1 = N)
    assertEquals(Machine.step(userMode).fault, Some(Fault.IllegalInMode("CP")))

    val pastEdge = Asm.page(ProgPage) { raw(Asm.ref(3, Z, 39)) }
    assertEquals(Machine.step(pastEdge.boot).fault, Some(Fault.PageFault))

    val rmc = Asm.page(ProgPage) { op(SpecialOp.RMC) }
    assertEquals(Machine.step(rmc.boot).fault, Some(Fault.Unimplemented("RMC")))

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

  test("trace returns the full step history"):
    val prog = Asm.page(ProgPage):
      push(const(5)); push(const(7)); op(BasicOp.SAddT)
    val history = Machine.trace(prog.boot, 3).toList
    assertEquals(history.length, 4)
    assertEquals(history.head, prog.boot)
    assertEquals(history.map(_.operands.length), List(0, 1, 2, 1))
