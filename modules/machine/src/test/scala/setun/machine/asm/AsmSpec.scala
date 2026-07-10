package setun.machine.asm

import setun.core.*
import setun.core.dsl.*
import setun.machine.*
import Asm.*

class AsmSpec extends munit.FunSuite:

  test("encode/decode round-trips all 54 opcodes"):
    for op <- BasicOp.values do
      assertEquals(Instruction.decode(Instruction.encode(op)), Instruction.Basic(op))
    for op <- SpecialOp.values do
      assertEquals(Instruction.decode(Instruction.encode(op)), Instruction.Special(op))

  test("constant pool deduplicates and grows from the page tail"):
    Asm.page(5):
      val a = const(5)
      val b = const(5)
      assertEquals(a, b)
      assertEquals(a, Ref(1, 40))
      assertEquals(const(7), Ref(1, 39))
      assertEquals(constSenior(7), Ref(3, 36)) // distinct from const(7)
      push(a)

  test("forward jump resolves through the label fixup"):
    val prog = Asm.page(5):
      push(const(7))
      jump("end")
      op(BasicOp.SAddT) // would underflow if executed
      label("end")
      op(BasicOp.TDN)
    val out = Machine.run(prog.boot, 4) // push, push slot, C=T, TDN
    assertEquals(out.fault, None)
    assertEquals(out.operands, List((-7L).bw))
    assertEquals(prog.labels("end"), -36)

  test("backward jump returns to a seen label"):
    val prog = Asm.page(5):
      label("start")
      push(const(1))
      jump("start")
    val out = Machine.run(prog.boot, 3)
    assertEquals(out.fault, None)
    assertEquals(out.cOffset, prog.labels("start"))
    assertEquals(out.operands, List(1L.bw))

  test("undefined label and double placement are assembly errors"):
    intercept[AssemblyError]:
      Asm.page(5) { jump("nowhere") }
    intercept[AssemblyError]:
      Asm.page(5):
        data(-40, Seq(Tryte.Zero)) // collides with the first code cell
        op(BasicOp.TDN)

  test("code colliding with the constant pool is an assembly error"):
    intercept[AssemblyError]:
      Asm.page(5):
        val c = const(1)
        for _ <- 1 to 81 do push(c)

  test("wide literals pool as full words"):
    val prog = Asm.page(5):
      push(const(100_000)) // > tryte range, needs 3 trytes
    val out = Machine.run(prog.boot, 1)
    assertEquals(out.operands, List(100_000L.bw))

  test("end-to-end: (5 + 7) × 3 in the DSL"):
    val prog = Asm.page(5):
      push(const(5))
      push(const(7))
      op(BasicOp.SAddT)
      push(const(3))
      op(BasicOp.LMulT)        // R := 12, S:Y := 3 × 12 × 3²
      push(constSenior(16))
      op(BasicOp.LST)          // S:Y <<= 16 brings the product into S
    val out = Machine.run(prog.boot, 7)
    assertEquals(out.fault, None)
    assertEquals(out.operands.head.toLong, 36L)
    assertEquals(out.r.toLong, 12L)
