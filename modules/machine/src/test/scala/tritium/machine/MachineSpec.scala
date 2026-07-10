package tritium.machine

import tritium.core.*
import tritium.core.dsl.*
import Trit.{N, Z, P}

class MachineSpec extends munit.FunSuite:

  val ProgPage = 5 // RAM, so W=S tests can write to it

  /** Reference tryte: length 1–3 trytes, page register h[hReg], address. */
  def ref(len: Int, hReg: Trit, addr: Int): Tryte =
    Tryte(Vector(Trit.fromValue(len - 2), hReg) ++ Trits.fromLong(addr, 4))

  /** A word laid out in memory as 3 trytes at addr (big-endian). */
  def wordAt(addr: Int, w: Word): Seq[(Int, Tryte)] =
    val (a, b, c) = w.trytes
    Seq(addr -> a, (addr + 1) -> b, (addr + 2) -> c)

  /** A word whose senior tryte is `v` — for jump offsets, shift counts,
    * E=T operands: the machine reads them from t, the senior tryte of T.
    */
  def senior(v: Int): Word = Word.fromTrytes(Tryte.fromInt(v), Tryte.Zero, Tryte.Zero)

  def machine(program: Seq[Tryte], data: (Int, Tryte)*): MachineState =
    val entries = program.zipWithIndex.map((k, i) => (Memory.MinAddr + i) -> k) ++ data
    val page = entries.foldLeft(Memory.BlankPage) { case (p, (a, t)) => p.updated(a + 40, t) }
    MachineState
      .initial(Memory(Map(ProgPage -> page)))
      .copy(cPage = ProgPage, cOffset = Memory.MinAddr)
      .copy(h = Map(N -> ProgPage, Z -> ProgPage, P -> ProgPage))

  test("operand refs push, S+T adds"):
    val m = machine(
      Seq(ref(1, Z, 10), ref(1, Z, 11), Tryte.parse("000+0+")),
      10 -> 5.bt,
      11 -> 7.bt,
    )
    val out = Machine.run(m, 3)
    assertEquals(out.fault, None)
    assertEquals(out.operands, List(12L.bw))

  test("RPN program: (a + b) × c via S+T, L*T, LST"):
    // L*T leaves T×R×3² in the pair S:Y; shifting the pair left by 16
    // brings the product into S (the paper-authentic scaling dance).
    val m = machine(
      Seq(
        ref(1, Z, 10),           // push a = 5
        ref(1, Z, 11),           // push b = 7
        Tryte.parse("000+0+"),   // S+T   → 12
        ref(1, Z, 12),           // push c = 3
        Tryte.parse("000++0"),   // L*T   → R := 12, S:Y := 3 × 12 × 9
        ref(3, Z, 13),           // push shift count 16 (senior tryte)
        Tryte.parse("000---"),   // LST   → S:Y <<= 16, S = 36
      ),
      (Seq(10 -> 5.bt, 11 -> 7.bt, 12 -> 3.bt) ++ wordAt(13, senior(16)))*,
    )
    val out = Machine.run(m, 7)
    assertEquals(out.fault, None)
    assertEquals(out.operands.head.toLong, 36L)
    assertEquals(out.r.toLong, 12L)
    assertEquals(out.y, Word.Zero)

  test("CGT jumps on S > 0 and sets ca := t"):
    val prog = Seq(ref(1, Z, 10), ref(3, Z, 11), Tryte.parse("0000-+"))
    val taken = machine(prog, (Seq(10 -> 30.bt) ++ wordAt(11, senior(0)))*)
    val out = Machine.run(taken, 3)
    assertEquals(out.fault, None)
    assertEquals(out.cOffset, 0)
    assertEquals(out.operands, List(30L.bw))

    val notTaken = machine(prog, (Seq(10 -> (-30).bt) ++ wordAt(11, senior(0)))*)
    val out2 = Machine.run(notTaken, 3)
    assertEquals(out2.cOffset, Memory.MinAddr + 3)
    assertEquals(out2.operands, List((-30L).bw))

  test("T=W loads through a reference in t"):
    val m = machine(Seq(Tryte.parse("0000+-")), 20 -> 9.bt)
      .copy(operands = List(Word.fromTrytes(ref(1, Z, 20), Tryte.Zero, Tryte.Zero)))
    val out = Machine.step(m)
    assertEquals(out.fault, None)
    assertEquals(out.operands, List(9L.bw))

  test("W=S writes S's low trytes through a reference in t"):
    val m = machine(Seq(Tryte.parse("0000++")))
      .copy(operands = List(Word.fromTrytes(ref(1, Z, 20), Tryte.Zero, Tryte.Zero), 42L.bw))
    val out = Machine.step(m)
    assertEquals(out.fault, None)
    assertEquals(out.operands, List(42L.bw))
    assertEquals(out.memory.tryte(ProgPage, 20), Right(42.bt))

  test("exponent register ops"):
    val m = machine(Seq("000-0+", "000-0+", "000-0-").map(Tryte.parse))
    assertEquals(Machine.run(m, 3).e, 1.bt)

  test("TDN negates in place"):
    val m = machine(Seq(Tryte.parse("000+00"))).copy(operands = List(5L.bw))
    assertEquals(Machine.step(m).operands, List((-5L).bw))

  test("faults: underflow, mode violation, page overrun, unimplemented"):
    val underflow = machine(Seq(Tryte.parse("000+0+")))
    assertEquals(Machine.step(underflow).fault, Some(Fault.StackUnderflow))

    val userMode = machine(Seq(Tryte.parse("00+0--"))).copy(c1 = N)
    assertEquals(Machine.step(userMode).fault, Some(Fault.IllegalInMode("CP")))

    val pastEdge = machine(Seq(ref(3, Z, 39)))
    assertEquals(Machine.step(pastEdge).fault, Some(Fault.PageFault))

    val rmc = machine(Seq(Tryte.parse("00+000")))
    assertEquals(Machine.step(rmc).fault, Some(Fault.Unimplemented("RMC")))

  test("CP/EXP/LP move the stack pointer registers"):
    val m = machine(Seq("00+0--", "00+0-0", "00+0-+").map(Tryte.parse))
      .copy(ph = Vector(Z, P), pa = Vector(P, Z, N))
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
    val m = machine(Seq(Tryte.parse("00+0+0")))
      .copy(operands = List(Word(Trits.zero(15).patch(3, Trits.fromLong(-13, 3), 3) ++ Trits.zero(3))))
    val out = Machine.step(m)
    assertEquals(out.fault, None)
    assertEquals(out.h(Z), -13)
    assertEquals(out.operands, Nil)

  test("trace returns the full step history"):
    val m = machine(
      Seq(ref(1, Z, 10), ref(1, Z, 11), Tryte.parse("000+0+")),
      10 -> 5.bt,
      11 -> 7.bt,
    )
    val history = Machine.trace(m, 3).toList
    assertEquals(history.length, 4)
    assertEquals(history.head, m)
    assertEquals(history.map(_.operands.length), List(0, 1, 2, 1))
