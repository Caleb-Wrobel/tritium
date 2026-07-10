package setun.core

/** Width-generic balanced ternary arithmetic on trit vectors (MSB first).
  * Low-level: `Tryte` and `Word` delegate here, and the machine module
  * uses it for register-field packing. Prefer the typed wrappers.
  */
object Trits:

  def zero(width: Int): Vector[Trit] = Vector.fill(width)(Trit.Z)

  def toLong(trits: Vector[Trit]): Long =
    trits.foldLeft(0L)((acc, t) => acc * 3 + t.value)

  def maxValue(width: Int): Long =
    (BigInt(3).pow(width).toLong - 1) / 2

  def fromLong(n: Long, width: Int): Vector[Trit] =
    require(math.abs(n) <= maxValue(width), s"$n out of $width-trit range")
    var rem = n
    val out = Array.fill(width)(Trit.Z)
    var i = width - 1
    while rem != 0 do
      val r = (((rem % 3) + 3) % 3) match
        case 2 => -1
        case v => v.toInt
      out(i) = Trit.fromValue(r)
      rem = (rem - r) / 3
      i -= 1
    out.toVector

  def parse(s: String, width: Int): Vector[Trit] =
    require(s.length <= width, s"'$s' has more than $width trits")
    val padded = ("0" * (width - s.length)) + s
    padded.map(Trit.fromSymbol).toVector

  /** Ripple-carry addition of equal-length vectors: (sum, final carry). */
  def add(a: Vector[Trit], b: Vector[Trit]): (Vector[Trit], Trit) =
    require(a.length == b.length)
    var carry = Trit.Z
    val out = Array.fill(a.length)(Trit.Z)
    for i <- (a.length - 1) to 0 by -1 do
      val s = a(i).value + b(i).value + carry.value
      // s ∈ [-3, 3]; digit is s mod 3 balanced, carry is the rest
      val digit = ((s + 4) % 3) - 1
      out(i) = Trit.fromValue(digit)
      carry = Trit.fromValue((s - digit) / 3)
    (out.toVector, carry)

  def negate(trits: Vector[Trit]): Vector[Trit] = trits.map(t => -t)

  /** Sign-extend to `width` trits (prepend zeros). */
  def extend(trits: Vector[Trit], width: Int): Vector[Trit] =
    zero(width - trits.length) ++ trits

  /** Shift-add multiplication in an extended accumulator wide enough to
    * never overflow; caller narrows the result back down.
    */
  def multiply(a: Vector[Trit], b: Vector[Trit]): Vector[Trit] =
    val width = a.length + b.length
    val wideA = extend(a, width)
    var acc = zero(width)
    // walk b from LSB; shift a left by the trit's position
    for (t, posFromMsb) <- b.zipWithIndex if t != Trit.Z do
      val shift = b.length - 1 - posFromMsb
      val term = wideA.drop(shift) ++ zero(shift)
      val signed = if t == Trit.N then negate(term) else term
      val (sum, carry) = add(acc, signed)
      assert(carry == Trit.Z, "accumulator sized to preclude carry-out")
      acc = sum
    acc
