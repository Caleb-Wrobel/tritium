package setun.machine.asm

import scala.collection.mutable
import setun.core.*
import setun.machine.*
import Trit.{N, Z, P}

final case class AssemblyError(message: String) extends Exception(message)

/** A pooled operand: `length` trytes at in-page address `addr`. */
final case class Ref private[asm] (length: Int, addr: Int)

/** An assembled page, ready to load. */
final case class Program(
    pageIndex: Int,
    page: Vector[Tryte],
    entry: Int,
    labels: Map[String, Int],
):
  def memory: Memory = Memory(Map(pageIndex -> page))

  /** Boot state: system mode at `entry`, all page registers on this page. */
  def boot: MachineState =
    MachineState
      .initial(memory)
      .copy(
        cPage = pageIndex,
        cOffset = entry,
        h = Map(N -> pageIndex, Z -> pageIndex, P -> pageIndex),
      )

/** Two-pass page assembler. Code grows from the page head (−40 up), the
  * constant pool from the page tail (+40 down); collision is an error.
  * Constants are deduplicated; labels resolve at `finish`, so forward
  * jumps work. Use through the `Asm.page` DSL:
  *
  * {{{
  * val prog = Asm.page(5):
  *   push(const(5)); push(const(7)); op(BasicOp.SAddT)
  *   jumpIfPos("done")
  *   label("done")
  * }}}
  */
final class AsmBuilder private[asm] (val pageIndex: Int):
  private val cells = mutable.Map.empty[Int, Tryte]
  private var pc = Memory.MinAddr
  private var poolNext = Memory.MaxAddr
  private val pool = mutable.Map.empty[(String, Long), Ref]
  private val labelAddrs = mutable.Map.empty[String, Int]
  private val fixups = mutable.ListBuffer.empty[(Int, String)]

  private def place(addr: Int, t: Tryte): Unit =
    if addr < Memory.MinAddr || addr > Memory.MaxAddr then
      throw AssemblyError(s"address $addr outside the page")
    if cells.contains(addr) then throw AssemblyError(s"cell $addr assembled twice")
    cells(addr) = t

  /** Emit one raw tryte at the next code address. */
  def raw(t: Tryte): Unit =
    if pc > poolNext then throw AssemblyError("code ran into the constant pool")
    place(pc, t)
    pc += 1

  private def alloc(trytes: Vector[Tryte]): Ref =
    val start = poolNext - trytes.length + 1
    if start < pc then throw AssemblyError("constant pool ran into code")
    trytes.zipWithIndex.foreach((t, i) => place(start + i, t))
    poolNext = start - 1
    Ref(trytes.length, start)

  /** Pool a literal: one tryte if it fits (±364), else a full word.
    * (Two-tryte operands are skipped: with page register h[0] they would
    * decode as opcodes — the spec's k[1:2] = 0 rule.)
    */
  def const(v: Int): Ref =
    pool.getOrElseUpdate(
      ("const", v),
      if math.abs(v) <= Tryte.MaxValue then alloc(Vector(Tryte.fromInt(v)))
      else
        val (a, b, c) = Word.fromLong(v).trytes
        alloc(Vector(a, b, c)),
    )

  /** Pool a word with `v` in the senior tryte — the convention for shift
    * counts, E=T operands, and jump offsets (the machine reads `t`).
    */
  def constSenior(v: Int): Ref =
    pool.getOrElseUpdate(("senior", v), alloc(Vector(Tryte.fromInt(v), Tryte.Zero, Tryte.Zero)))

  private def labelSlot(name: String): Ref =
    pool.getOrElseUpdate(
      (s"label:$name", 0L), {
        val r = alloc(Vector(Tryte.Zero, Tryte.Zero, Tryte.Zero))
        fixups += ((r.addr, name))
        r
      },
    )

  /** Emit an operand-reference tryte for a pooled value (via h[0]). */
  def push(r: Ref): Unit = raw(Asm.ref(r.length, Z, r.addr))

  def op(o: BasicOp): Unit = raw(Instruction.encode(o))
  def op(o: SpecialOp): Unit = raw(Instruction.encode(o))

  def label(name: String): Unit =
    if labelAddrs.contains(name) then throw AssemblyError(s"duplicate label '$name'")
    labelAddrs(name) = pc

  def jump(name: String): Unit = { push(labelSlot(name)); op(BasicOp.CSetT) }
  def jumpIfNeg(name: String): Unit = { push(labelSlot(name)); op(BasicOp.CLT) }
  def jumpIfZero(name: String): Unit = { push(labelSlot(name)); op(BasicOp.CET) }
  def jumpIfPos(name: String): Unit = { push(labelSlot(name)); op(BasicOp.CGT) }

  /** Explicit placement, e.g. for memory a program reads through T=W. */
  def data(addr: Int, trytes: Seq[Tryte]): Unit =
    trytes.zipWithIndex.foreach((t, i) => place(addr + i, t))

  def word(addr: Int, w: Word): Unit =
    val (a, b, c) = w.trytes
    data(addr, Seq(a, b, c))

  private[asm] def finish(): Program =
    for (addr, name) <- fixups do
      val target = labelAddrs.getOrElse(name, throw AssemblyError(s"undefined label '$name'"))
      cells(addr) = Tryte.fromInt(target) // patch the slot's senior tryte
    val page = (Memory.MinAddr to Memory.MaxAddr).toVector.map(a => cells.getOrElse(a, Tryte.Zero))
    Program(pageIndex, page, Memory.MinAddr, labelAddrs.toMap)

object Asm:
  def page(pageIndex: Int)(body: AsmBuilder ?=> Unit): Program =
    val b = AsmBuilder(pageIndex)
    body(using b)
    b.finish()

  /** A standalone operand-reference tryte (also usable as data). */
  def ref(len: Int, hReg: Trit, addr: Int): Tryte =
    if len < 1 || len > 3 then throw AssemblyError(s"operand length $len not in 1..3")
    if len == 2 && hReg == Z then
      throw AssemblyError("2-tryte operands can't address via h[0] (would decode as an opcode)")
    Tryte(Vector(Trit.fromValue(len - 2), hReg) ++ Trits.fromLong(addr, 4))

  // contextual sugar so `Asm.page(5) { push(const(5)); op(SAddT) }` reads plainly
  def const(v: Int)(using b: AsmBuilder): Ref = b.const(v)
  def constSenior(v: Int)(using b: AsmBuilder): Ref = b.constSenior(v)
  def push(r: Ref)(using b: AsmBuilder): Unit = b.push(r)
  def raw(t: Tryte)(using b: AsmBuilder): Unit = b.raw(t)
  def op(o: BasicOp)(using b: AsmBuilder): Unit = b.op(o)
  def op(o: SpecialOp)(using b: AsmBuilder): Unit = b.op(o)
  def label(n: String)(using b: AsmBuilder): Unit = b.label(n)
  def jump(n: String)(using b: AsmBuilder): Unit = b.jump(n)
  def jumpIfNeg(n: String)(using b: AsmBuilder): Unit = b.jumpIfNeg(n)
  def jumpIfZero(n: String)(using b: AsmBuilder): Unit = b.jumpIfZero(n)
  def jumpIfPos(n: String)(using b: AsmBuilder): Unit = b.jumpIfPos(n)
  def data(addr: Int, ts: Seq[Tryte])(using b: AsmBuilder): Unit = b.data(addr, ts)
  def word(addr: Int, w: Word)(using b: AsmBuilder): Unit = b.word(addr, w)
