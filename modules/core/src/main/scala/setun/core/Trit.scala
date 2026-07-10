package setun.core

/** A balanced ternary digit: -1 (N), 0 (Z), or +1 (P). */
enum Trit(val value: Int, val symbol: Char):
  case N extends Trit(-1, '-')
  case Z extends Trit(0, '0')
  case P extends Trit(1, '+')

  def unary_- : Trit = this match
    case N => P
    case Z => Z
    case P => N

  /** Full addition of two trits: (sum, carry). */
  def +(that: Trit): (Trit, Trit) =
    Trit.fromCarrySum(value + that.value)

  def *(that: Trit): Trit = Trit.fromValue(value * that.value)

object Trit:
  def fromValue(v: Int): Trit = v match
    case -1 => N
    case 0  => Z
    case 1  => P
    case _  => throw IllegalArgumentException(s"not a trit value: $v")

  def fromSymbol(c: Char): Trit = c match
    case '-' => N
    case '0' => Z
    case '+' => P
    case _   => throw IllegalArgumentException(s"not a trit symbol: $c")

  /** Decompose an integer sum in [-2, 2] into (sum trit, carry trit). */
  private[core] def fromCarrySum(s: Int): (Trit, Trit) = s match
    case -2 => (P, N)
    case 2  => (N, P)
    case v  => (fromValue(v), Z)
