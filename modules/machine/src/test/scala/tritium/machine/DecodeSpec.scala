package tritium.machine

import tritium.core.*
import tritium.core.dsl.*
import Trit.{N, Z, P}

class DecodeSpec extends munit.FunSuite:
  import Instruction.*

  test("basic opcodes decode per the spec table"):
    assertEquals(decode(t"000---"), Basic(BasicOp.LST))
    assertEquals(decode(t"000-00"), Basic(BasicOp.EClr))
    assertEquals(decode(t"0000--"), Basic(BasicOp.CLT))
    assertEquals(decode(t"000000"), Basic(BasicOp.RSetT))
    assertEquals(decode(t"0000+-"), Basic(BasicOp.TSetW))
    assertEquals(decode(t"000+00"), Basic(BasicOp.TDN))
    assertEquals(decode(t"000+0+"), Basic(BasicOp.SAddT))
    assertEquals(decode(t"000+++"), Basic(BasicOp.LHT))

  test("special opcodes decode per the spec table"):
    assertEquals(decode(t"00+---"), Special(SpecialOp.CG1))
    assertEquals(decode(t"00+0--"), Special(SpecialOp.CP))
    assertEquals(decode(t"00+000"), Special(SpecialOp.RMC))
    assertEquals(decode(t"00+0+-"), Special(SpecialOp.LH1))
    assertEquals(decode(t"00++++"), Special(SpecialOp.LG3))

  test("mnemonics match the spec"):
    val basics = "LST COT XNN E-1 E=0 E+1 T-E E=T T+E CLT CET CGT T=C R=T C=T " +
      "T=W YFT W=S SMT Y=T SAT S-T TDN S+T LBT L*T LHT"
    assertEquals(BasicOp.values.toList.map(_.mnemonic), basics.split(' ').toList)
    val specials = "CG1 CG2 CG3 CF1 CF2 CF3 LQ1 LQ2 LQ3 CP EXP LP CMC RMC LMC " +
      "LH1 LH2 LH3 LU1 LU2 LU3 LF1 LF2 LF3 LG1 LG2 LG3"
    assertEquals(SpecialOp.values.toList.map(_.mnemonic), specials.split(' ').toList)

  test("k1,k2,k3 = 0,0,- is a macro-operation carrying ka = k[3:6]"):
    assertEquals(decode(t"00-0+-"), MacroCall(Vector(N, Z, P, N)))

  test("nonzero k1/k2 is an operand reference"):
    assertEquals(decode(t"+0++++"), OperandRef(3, Z, Vector(P, P, P, P)))
    assertEquals(decode(t"0-0000"), OperandRef(2, N, Vector(Z, Z, Z, Z)))
    assertEquals(decode(t"-+00-0"), OperandRef(1, P, Vector(Z, Z, N, Z)))

  test("all 729 trytes decode, in the right proportions"):
    val all =
      for
        a <- Trit.values; b <- Trit.values; c <- Trit.values
        d <- Trit.values; e <- Trit.values; f <- Trit.values
      yield decode(Tryte(Vector(a, b, c, d, e, f)))
    assertEquals(all.count(_.isInstanceOf[Basic]), 27)
    assertEquals(all.count(_.isInstanceOf[Special]), 27)
    assertEquals(all.count(_.isInstanceOf[MacroCall]), 27)
    assertEquals(all.count(_.isInstanceOf[OperandRef]), 648)
