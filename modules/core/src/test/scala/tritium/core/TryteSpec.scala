package tritium.core

import tritium.core.dsl.*

class TryteSpec extends munit.FunSuite:

  test("round-trips every value in tryte range"):
    for n <- Tryte.MinValue to Tryte.MaxValue do
      assertEquals(Tryte.fromInt(n).toInt, n)

  test("negation is arithmetic negation"):
    for n <- Tryte.MinValue to Tryte.MaxValue do
      assertEquals((-Tryte.fromInt(n)).toInt, -n)

  test("addition matches integer addition"):
    val rng = scala.util.Random(70)
    for _ <- 1 to 10_000 do
      val a = rng.between(-180, 181)
      val b = rng.between(-180, 181)
      assertEquals((a.bt + b.bt).toInt, a + b)

  test("literal syntax parses and pads"):
    assertEquals(t"+0-".toInt, 8)
    assertEquals(t"+".toInt, 1)
    assertEquals(t"-".toInt, -1)
    assertEquals(t"000000", Tryte.Zero)

  test("overflow raises"):
    intercept[ArithmeticException]:
      Tryte.fromInt(Tryte.MaxValue) + 1.bt
