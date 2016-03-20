tritium

A rudimentary simulated balanced ternary processor written in Java.


The ternary digit is called the trit. The ternary digit is represented by the enum class `Trit`. At trit can take one of three values which are represented by `Trit.O`, `Trit.I`, and `Trit.T`
Trits can have both numeric and logical meanings according to this table:

Trit | Numeric | Logical
-----|---------|--------
O | 0 | Unknown
I | 1 | True
T | -1 | False

The values of trit will be represented as the characters 'O', 'I', and 'T' corresponding to the names of the enums representing them and the value it represents will be inferred by its context. Note that these characters were chosen to resemble the numeric values the trit can take. In particular the trit 'T' resemble -1 amd __does not__ mean true when  used as a logical value.
