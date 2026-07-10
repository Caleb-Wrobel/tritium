package setun.core

/** A Setun-70 operand-stack word: 18 trits (3 trytes), MSB first.
  * Range: ±193 710 244 (i.e. ±(3^18 - 1)/2).
  */
final case class Word(trits: Vector[Trit]):
  require(trits.length == Word.Width, s"a word is ${Word.Width} trits, got ${trits.length}")

  def toLong: Long = Trits.toLong(trits)

  def unary_- : Word = Word(Trits.negate(trits))

  def +(that: Word): Word =
    val (sum, carry) = Trits.add(trits, that.trits)
    if carry != Trit.Z then throw ArithmeticException(s"word overflow: $this + $that")
    Word(sum)

  def -(that: Word): Word = this + (-that)

  def *(that: Word): Word =
    val wide = Trits.multiply(trits, that.trits)
    val value = Trits.toLong(wide)
    if math.abs(value) > Word.MaxValue then
      throw ArithmeticException(s"word overflow: $this * $that")
    Word(wide.drop(wide.length - Word.Width))

  /** Shift toward MSB (multiply by 3); overflow if a nonzero trit falls off. */
  def <<(n: Int): Word =
    require(n >= 0)
    if trits.take(n).exists(_ != Trit.Z) then
      throw ArithmeticException(s"word overflow: $this << $n")
    Word(trits.drop(n) ++ Trits.zero(n))

  /** Shift toward LSB (divide by 3). In balanced ternary, dropping the low
    * trit rounds to the *nearest* integer — no floor/ceil asymmetry.
    */
  def >>(n: Int): Word =
    require(n >= 0)
    Word(Trits.zero(n) ++ trits.dropRight(n))

  /** The three trytes of this word, most significant first. */
  def trytes: (Tryte, Tryte, Tryte) =
    val Vector(a, b, c) = trits.grouped(Tryte.Width).map(Tryte(_)).toVector
    (a, b, c)

  override def toString: String = trits.map(_.symbol).mkString

object Word:
  val Width = 18
  val MaxValue: Long = Trits.maxValue(Width)
  val MinValue: Long = -MaxValue

  val Zero: Word = Word(Trits.zero(Width))

  def fromLong(n: Long): Word = Word(Trits.fromLong(n, Width))

  def parse(s: String): Word = Word(Trits.parse(s, Width))

  def fromTrytes(hi: Tryte, mid: Tryte, lo: Tryte): Word =
    Word(hi.trits ++ mid.trits ++ lo.trits)
