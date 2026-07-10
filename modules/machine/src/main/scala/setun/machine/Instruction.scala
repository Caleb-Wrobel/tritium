package setun.machine

import setun.core.*
import Trit.{N, Z, P}

/** Basic operations B1–B27, declared in 3-code order `000---` … `000+++`
  * so that ordinal = code value + 13. Mnemonics follow the spec
  * (docs/setun-70/instruction-set.md).
  */
enum BasicOp(val mnemonic: String):
  case LST   extends BasicOp("LST")
  case COT   extends BasicOp("COT")
  case XNN   extends BasicOp("XNN")
  case EDec  extends BasicOp("E-1")
  case EClr  extends BasicOp("E=0")
  case EInc  extends BasicOp("E+1")
  case TSubE extends BasicOp("T-E")
  case ESetT extends BasicOp("E=T")
  case TAddE extends BasicOp("T+E")
  case CLT   extends BasicOp("CLT")
  case CET   extends BasicOp("CET")
  case CGT   extends BasicOp("CGT")
  case TSetC extends BasicOp("T=C")
  case RSetT extends BasicOp("R=T")
  case CSetT extends BasicOp("C=T")
  case TSetW extends BasicOp("T=W")
  case YClr  extends BasicOp("YFT")
  case WSetS extends BasicOp("W=S")
  case SMT   extends BasicOp("SMT")
  case YSetT extends BasicOp("Y=T")
  case SAT   extends BasicOp("SAT")
  case SSubT extends BasicOp("S-T")
  case TDN   extends BasicOp("TDN")
  case SAddT extends BasicOp("S+T")
  case LBT   extends BasicOp("LBT")
  case LMulT extends BasicOp("L*T")
  case LHT   extends BasicOp("LHT")

/** Special operations S1–S27, declared in 3-code order `00+---` … `00++++`. */
enum SpecialOp(val mnemonic: String):
  case CG1 extends SpecialOp("CG1")
  case CG2 extends SpecialOp("CG2")
  case CG3 extends SpecialOp("CG3")
  case CF1 extends SpecialOp("CF1")
  case CF2 extends SpecialOp("CF2")
  case CF3 extends SpecialOp("CF3")
  case LQ1 extends SpecialOp("LQ1")
  case LQ2 extends SpecialOp("LQ2")
  case LQ3 extends SpecialOp("LQ3")
  case CP  extends SpecialOp("CP")
  case EXP extends SpecialOp("EXP")
  case LP  extends SpecialOp("LP")
  case CMC extends SpecialOp("CMC")
  case RMC extends SpecialOp("RMC")
  case LMC extends SpecialOp("LMC")
  case LH1 extends SpecialOp("LH1")
  case LH2 extends SpecialOp("LH2")
  case LH3 extends SpecialOp("LH3")
  case LU1 extends SpecialOp("LU1")
  case LU2 extends SpecialOp("LU2")
  case LU3 extends SpecialOp("LU3")
  case LF1 extends SpecialOp("LF1")
  case LF2 extends SpecialOp("LF2")
  case LF3 extends SpecialOp("LF3")
  case LG1 extends SpecialOp("LG1")
  case LG2 extends SpecialOp("LG2")
  case LG3 extends SpecialOp("LG3")

/** A decoded instruction tryte `k = k1,k2,k3,k4,k5,k6`:
  *   - `k1,k2 = 0,0`: opcode — basic (`k3 = 0`), special (`k3 = +`), or
  *     macro-operation / system call (`k3 = −`, with `ka = k[3:6]`)
  *   - otherwise: operand reference — length `k1 + 2` trytes, page
  *     register `h[k2]`, in-page address `k[3:6]`
  */
enum Instruction:
  case Basic(op: BasicOp)
  case Special(op: SpecialOp)
  case MacroCall(ka: Vector[Trit])
  case OperandRef(length: Int, hReg: Trit, addr: Vector[Trit])

object Instruction:
  def decode(k: Tryte): Instruction =
    val ts = k.trits
    (ts(0), ts(1)) match
      case (Z, Z) =>
        val idx = Trits.toLong(ts.slice(3, 6)).toInt + 13
        ts(2) match
          case Z => Basic(BasicOp.fromOrdinal(idx))
          case P => Special(SpecialOp.fromOrdinal(idx))
          case N => MacroCall(ts.slice(2, 6))
      case (k1, k2) =>
        OperandRef(k1.value + 2, k2, ts.slice(2, 6))
