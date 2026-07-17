package setun.machine

import setun.core.*
import setun.core.dsl.*
import Trit.Z

class TapeCodeSpec extends munit.FunSuite:
  val allTrytes: Seq[Tryte] =
    (Tryte.MinValue to Tryte.MaxValue).map(Tryte.fromInt)

  def unipolar(t: Tryte): Boolean =
    val signs = t.trits.filter(_ != Z).distinct
    signs.length <= 1

  test("encode is defined exactly on the unipolar trytes"):
    for t <- allTrytes do
      assertEquals(TapeCode.encode(t).isDefined, unipolar(t), t.toString)
    assertEquals(allTrytes.count(t => TapeCode.encode(t).isDefined), TapeCode.Symbols)

  test("decode(encode(t)) == t for every encodable tryte"):
    for t <- allTrytes; row <- TapeCode.encode(t) do
      assertEquals(TapeCode.decode(row), t, s"row $row")

  test("encode(decode(row)) == row for every row, blank normalizing to hole-7-only"):
    for row <- 0 until 128 do
      val canonical = if row == 0 then 64 else row
      assertEquals(TapeCode.encode(TapeCode.decode(row)), Some(canonical), s"row $row")

  test("the mapping is element-wise, not a binary-value conversion"):
    // holes 1 and 2 with hole 7: trits at the two lowest places, not fromInt(3)
    assertEquals(TapeCode.decode(64 | 3), t"0000++") // +4
    assertEquals(TapeCode.decode(3), t"0000--")      // -4
    assertNotEquals(TapeCode.decode(64 | 3), Tryte.fromInt(3))
    // hole k alone carries weight 3^(k-1)
    for k <- 1 to 6 do
      assertEquals(TapeCode.decode(64 | (1 << (k - 1))).toInt, math.pow(3, k - 1).toInt)

  test("the canonical zero is hole-7-only; blank is its alias"):
    assertEquals(TapeCode.encode(Tryte.Zero), Some(64))
    assertEquals(TapeCode.decode(64), Tryte.Zero)
    assertEquals(TapeCode.decode(0), Tryte.Zero)

  test("rows outside 7 bits are rejected"):
    intercept[IllegalArgumentException](TapeCode.decode(128))
    intercept[IllegalArgumentException](TapeCode.decode(-1))
