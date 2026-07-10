package tritium.core

/** DSL entry points for balanced ternary literals and syntax.
  *
  * {{{
  * import tritium.core.dsl.*
  * val a = t"+0-"        // tryte literal (zero-padded to 6 trits)
  * val b = 42.bt         // Int -> Tryte
  * a + b
  * }}}
  */
object dsl:
  extension (sc: StringContext)
    def t(@annotation.unused args: Any*): Tryte = Tryte.parse(sc.parts.mkString)
    def w(@annotation.unused args: Any*): Word = Word.parse(sc.parts.mkString)

  extension (n: Int)
    def bt: Tryte = Tryte.fromInt(n)

  extension (n: Long)
    def bw: Word = Word.fromLong(n)
