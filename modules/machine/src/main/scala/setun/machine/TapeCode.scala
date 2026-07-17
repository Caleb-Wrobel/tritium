package setun.machine

import setun.core.*
import Trit.{N, Z, P}

/** The Setun-70 symbol code: one 7-hole punched-tape row (equivalently,
  * one 7-bit channel register value) per tryte. Bit k−1 of a row is hole
  * k. Per the correspondence rules in Брусенцов/Жоголев/Маслов, «Общая
  * характеристика малой цифровой машины „Сетунь-70"»:
  *
  *   1. holes 1–6 map to tryte elements 6–1 (hole 1 → the least
  *      significant trit, hole 6 → the most significant);
  *   2. an unpunched position is a zero trit;
  *   3. punched positions are +1 trits when hole 7 is punched,
  *   4. −1 trits when it is not;
  *   5. encoding punches every nonzero-trit position, and hole 7 exactly
  *      when the tryte contains no −1.
  *
  * The mapping is element-wise — each hole lands in its own trit place —
  * not an arithmetic conversion of the row's binary value. Only trytes
  * whose nonzero trits share one sign have a row (127 of 729), which is
  * the machine's character repertoire; `encode` is None outside it. The
  * paper notes the code's one ambiguity: a blank row and a row with only
  * hole 7 both decode to the zero tryte. Rule 5 makes hole-7-only the
  * canonical zero, so `encode(decode(row)) == Some(row)` for every row
  * except blank, which normalizes to hole-7-only.
  */
object TapeCode:
  /** Number of distinct encodable trytes: 63 all-plus + 63 all-minus + zero. */
  val Symbols = 127

  /** Decode a 7-bit row to its tryte. Total on 0–127. */
  def decode(row: Int): Tryte =
    require(0 <= row && row < 128, s"not a 7-bit tape row: $row")
    val sign = if (row & 64) != 0 then P else N
    Tryte(Vector.tabulate(Tryte.Width): i =>
      if (row & (1 << (Tryte.Width - 1 - i))) != 0 then sign else Z)

  /** Encode a tryte as a row, if its nonzero trits share one sign. */
  def encode(t: Tryte): Option[Int] =
    val holes = t.trits.zipWithIndex.collect:
      case (tr, i) if tr != Z => (tr, 1 << (Tryte.Width - 1 - i))
    holes.map(_._1).distinct match
      case Vector(N) => Some(holes.map(_._2).sum)
      case Vector(P) => Some(holes.map(_._2).sum | 64)
      case Vector()  => Some(64) // canonical zero: hole 7 only (rule 5)
      case _         => None // mixed signs: outside the symbol repertoire
