package tritium.core

import tritium.core.dsl.*

class WordSpec extends munit.FunSuite:
  val rng = scala.util.Random(70)
  def randomWord: Long = rng.between(Word.MinValue, Word.MaxValue + 1)

  test("round-trips random values across the word range"):
    for _ <- 1 to 10_000 do
      val n = randomWord
      assertEquals(Word.fromLong(n).toLong, n)
    assertEquals(Word.fromLong(Word.MaxValue).toLong, Word.MaxValue)
    assertEquals(Word.fromLong(Word.MinValue).toLong, Word.MinValue)

  test("addition and subtraction match Long arithmetic"):
    for _ <- 1 to 10_000 do
      val a = randomWord / 2
      val b = randomWord / 2
      assertEquals((a.bw + b.bw).toLong, a + b)
      assertEquals((a.bw - b.bw).toLong, a - b)

  test("multiplication matches Long arithmetic"):
    for _ <- 1 to 10_000 do
      val a = rng.between(-13_000L, 13_001L)
      val b = rng.between(-13_000L, 13_001L)
      assertEquals((a.bw * b.bw).toLong, a * b)

  test("multiplication overflow raises"):
    intercept[ArithmeticException]:
      Word.fromLong(Word.MaxValue) * 2L.bw

  test("left shift multiplies by 3, overflow raises"):
    for _ <- 1 to 1_000 do
      val a = randomWord / 100
      assertEquals((a.bw << 2).toLong, a * 9)
    intercept[ArithmeticException]:
      Word.fromLong(Word.MaxValue) << 1

  test("right shift rounds to nearest (balanced ternary truncation)"):
    for _ <- 1 to 10_000 do
      val a = randomWord
      val r = a % 3 match // balanced remainder in {-1, 0, 1}
        case 2 => -1
        case -2 => 1
        case v => v
      assertEquals((a.bw >> 1).toLong, (a - r) / 3)

  test("word literals and tryte composition"):
    val word = w"+0-"
    assertEquals(word.toLong, 8L)
    val (hi, mid, lo) = 42L.bw.trytes
    assertEquals(Word.fromTrytes(hi, mid, lo), 42L.bw)
    assertEquals(hi, Tryte.Zero)
    assertEquals(lo.toInt, 42)
