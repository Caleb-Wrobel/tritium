package tritium.core

/** A Setun-70 style tryte: 6 trits, most significant first.
  * Range: [-364, 364] (i.e. ±(3^6 - 1)/2).
  */
final case class Tryte(trits: Vector[Trit]):
  require(trits.length == Tryte.Width, s"a tryte is ${Tryte.Width} trits, got ${trits.length}")

  def toInt: Int = trits.foldLeft(0)((acc, t) => acc * 3 + t.value)

  def unary_- : Tryte = Tryte(trits.map(t => -t))

  def +(that: Tryte): Tryte =
    val (result, carry) = Tryte.addWithCarry(this, that)
    if carry != Trit.Z then throw ArithmeticException(s"tryte overflow: $this + $that")
    result

  def -(that: Tryte): Tryte = this + (-that)

  override def toString: String = trits.map(_.symbol).mkString

object Tryte:
  val Width = 6
  val MaxValue: Int = (math.pow(3, Width).toInt - 1) / 2
  val MinValue: Int = -MaxValue

  val Zero: Tryte = Tryte(Vector.fill(Width)(Trit.Z))

  def fromInt(n: Int): Tryte =
    require(n >= MinValue && n <= MaxValue, s"$n out of tryte range [$MinValue, $MaxValue]")
    var rem = n
    val out = Array.fill(Width)(Trit.Z)
    var i = Width - 1
    while rem != 0 do
      val r = ((rem % 3) + 3) % 3 match
        case 2 => -1
        case v => v
      out(i) = Trit.fromValue(r)
      rem = (rem - r) / 3
      i -= 1
    Tryte(out.toVector)

  def parse(s: String): Tryte =
    val padded = ("0" * (Width - s.length)) + s
    Tryte(padded.map(Trit.fromSymbol).toVector)

  /** Ripple-carry addition; returns (result, final carry). */
  def addWithCarry(a: Tryte, b: Tryte): (Tryte, Trit) =
    var carry = Trit.Z
    val out = Array.fill(Width)(Trit.Z)
    for i <- (Width - 1) to 0 by -1 do
      val s = a.trits(i).value + b.trits(i).value + carry.value
      // s ∈ [-3, 3]; digit is s mod 3 balanced, carry is the rest
      val digit = ((s + 4) % 3) - 1
      out(i) = Trit.fromValue(digit)
      carry = Trit.fromValue((s - digit) / 3)
    (Tryte(out.toVector), carry)
