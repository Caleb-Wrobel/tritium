package setun.core

/** A Setun-70 tryte: 6 trits, most significant first — the machine's
  * smallest addressable unit. Range: [-364, 364] (i.e. ±(3^6 - 1)/2).
  */
final case class Tryte(trits: Vector[Trit]):
  require(trits.length == Tryte.Width, s"a tryte is ${Tryte.Width} trits, got ${trits.length}")

  def toInt: Int = Trits.toLong(trits).toInt

  def unary_- : Tryte = Tryte(Trits.negate(trits))

  def +(that: Tryte): Tryte =
    val (result, carry) = Tryte.addWithCarry(this, that)
    if carry != Trit.Z then throw ArithmeticException(s"tryte overflow: $this + $that")
    result

  def -(that: Tryte): Tryte = this + (-that)

  override def toString: String = trits.map(_.symbol).mkString

object Tryte:
  val Width = 6
  val MaxValue: Int = Trits.maxValue(Width).toInt
  val MinValue: Int = -MaxValue

  val Zero: Tryte = Tryte(Trits.zero(Width))

  def fromInt(n: Int): Tryte = Tryte(Trits.fromLong(n, Width))

  def parse(s: String): Tryte = Tryte(Trits.parse(s, Width))

  /** Ripple-carry addition; returns (result, final carry). */
  def addWithCarry(a: Tryte, b: Tryte): (Tryte, Trit) =
    val (sum, carry) = Trits.add(a.trits, b.trits)
    (Tryte(sum), carry)
