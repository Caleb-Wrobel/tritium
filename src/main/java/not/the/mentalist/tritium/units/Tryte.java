package not.the.mentalist.tritium.units;

import java.util.Arrays;

import static not.the.mentalist.tritium.units.Trit.*;
//
///**
// * This class is copied from an earlier project to serve as a starting point.
// * Methods will be copied into the body of the class as they are implemented
// * 9 trit unit of data
// * <p>
// * The Tryte is the ternary equivalent of a byte and will be the primary unit used in tritium
// * Trytes are immutable and typeless in the context of this project. They may be used at integers, chars, etc...
// */
public class Tryte implements Comparable<Tryte> {

    // Placeholder
    public int compareTo(Tryte o) {
        return 0;
    }
}
//
//
//    //
//    final private Trit[] trits;
//
//    private Tryte(Trit[] field) {
//        if (field.length == Tryte_LENGTH) {
//            trits = Arrays.copyOf(field, Tryte_LENGTH);
//        } else {
//            Trit[] temp = ZERO.toArray();
//            if (field.length > Tryte_LENGTH) {
//                temp = Arrays.copyOfRange(field, field.length - Tryte_LENGTH,
//                        field.length);
//            } else {
//                for (int i = 1, n = field.length, m = Tryte_LENGTH; i <= n; i++) {
//                    temp[m - i] = field[n - i];
//                }
//            }
//
//            trits = temp;
//        }
//    }
//
//    //public Tryte valueOf()
//
//    // Accessor methods to individual bits
//    public Trit t(int pos) {
//        return trits[pos];
//    }
//
//    public Trit t0() {
//        return trits[0];
//    }
//
//    public Trit t1() {
//        return trits[0];
//    }
//
//    public Trit t2() {
//        return trits[0];
//    }
//
//    public Trit t3() {
//        return trits[0];
//    }
//
//    public Trit t4() {
//        return trits[0];
//    }
//
//    public Trit t5() {
//        return trits[0];
//    }
//
//    public Trit t6() {
//        return trits[0];
//    }
//
//    public Trit t7() {
//        return trits[0];
//    }
//
//    public Trit t8() {
//        return trits[0];
//    }
//
//
//    final private static int Tryte_LENGTH = 9;
//    final private static Tryte ZERO;
//    final private static Tryte MAX;
//    final private static Tryte MIN;
//    final private static Tryte ONE;
//    final private static Tryte[] PO2;
//    final private Trit[] trits;
//
//    static {
//        Trit[] z = new Trit[Tryte_LENGTH];
//        Arrays.fill(z, O);
//        ZERO = new Tryte(z);
//        z[17] = I;
//        ONE = new Tryte(z);
//        Arrays.fill(z, I);
//        MAX = new Tryte(z);
//        Arrays.fill(z, T);
//        MIN = new Tryte(z);
//        Tryte power = new Tryte(new Trit[]{O, O, O, O, O, O, O, O, O, O, O, O, O, O, O, O, O, I});
//        PO2 = new Tryte[28];
//        for (int i = 0; i < 28; i++) {
//            PO2[i] = power;
//            power = power.add(power);
//        }
//
//        for (Tryte e : PO2) {
//            System.out.println(e.toString() + " = " + e.toInt());
//        }
//    }
////
////    public Tryte() {
////        trits = Arrays.copyOf(ZERO.trits, Tryte_LENGTH);
////    }
////
////    //public Tryte(Tryte Tryte) {
////    //    trits = Tryte.toArray();
////    //}
////
////    public Trit[] toArray() {
////        return Arrays.copyOf(trits, Tryte_LENGTH);
////    }
////
////
////    public Tryte add(Tryte that) {
////        Trit[] this1 = this.toArray(), that1 = that.toArray(), res = new Trit[Tryte_LENGTH];
////        Trit carry = O;
////
////        for (int i = Tryte_LENGTH - 1; i >= 0; i--) {
////            Trit[] temp = Trit.triAdd(this1[i], that1[i], carry);
////            res[i] = temp[1];
////            carry = temp[0];
////        }
////
////        return new Tryte(res);
////    }
////
////
////    public Tryte sub(Tryte that) {
////        Trit[] this1 = this.toArray(), that1 = that.toArray(), res = new Trit[Tryte_LENGTH];
////        Trit carry = O;
////
////        for (int i = Tryte_LENGTH - 1; i >= 0; i--) {
////            Trit[] temp = Trit.triAdd(this1[i], that1[i].not(), carry);
////            res[i] = temp[1];
////            carry = temp[0];
////        }
////
////        return new Tryte(res);
////    }
////
////    public Tryte and(Tryte that) {
////        Trit[] res = new Trit[Tryte_LENGTH];
////        Trit[] this1 = this.toArray();
////        Trit[] that1 = that.toArray();
////        for (int i = 0; i < Tryte_LENGTH; i++) {
////            res[i] = this1[i].and(that1[i]);
////        }
////        return new Tryte(res);
////    }
////
////    public Tryte or(Tryte that) {
////        Trit[] res = new Trit[Tryte_LENGTH];
////        Trit[] this1 = this.toArray();
////        Trit[] that1 = that.toArray();
////        for (int i = 0; i < Tryte_LENGTH; i++) {
////            res[i] = this1[i].or(that1[i]);
////        }
////        return new Tryte(res);
////    }
////
////    public Tryte not() {
////        Trit[] res = new Trit[Tryte_LENGTH];
////        Trit[] ori = this.toArray();
////        for (int i = 0; i < Tryte_LENGTH; i++) {
////            res[i] = ori[i].not();
////        }
////        return new Tryte(res);
////    }
////
////    public Trit signum() {
////        Trit sig = O;
////        int i = 0;
////        while (sig == O && i < Tryte_LENGTH) {
////            sig = trits[i];
////            i++;
////        }
////        return sig;
////    }
//
//    // public static Tryte fromInt(int i){
//    //
//    // }
//
//    // private static Trit[] binaryToTernary(int i){
//    //
//    // }
//
//    private Tryte valueOf(Tryte Tryte) {
//        return Tryte;
//    }
//
//    public String toString() {
//        char[] cars = new char[Tryte_LENGTH];
//        for (int i = 0; i < Tryte_LENGTH; i++) {
//            cars[i] = trits[i].toChar();
//        }
//        return String.valueOf(cars);
//    }
//
//    public static Tryte zero() {
//        return ZERO;
//    }
//
//    public static Tryte one() {
//        return ONE;
//    }
//
//    public static Tryte min() {
//        return MIN;
//    }
//
//    public static Tryte max() {
//        return MAX;
//    }
//
//    public Trit lsTrit() {
//        return trits[Tryte_LENGTH - 1];
//    }
//
//    public Trit msTrit() {
//        return trits[0];
//    }
//
//
//    @Override
//    public int hashCode() {
//        return Arrays.hashCode(trits);
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (!(o instanceof Tryte)) {
//            return false;
//        } else {
//            Tryte w = (Tryte) o;
//            return (Arrays.equals(trits, w.trits));
//        }
//    }
//
//    public int compareTo(Tryte w) {
//        return this.sub(w).signum().toInt();
//    }
//
//    public int toInt() {
//        int t = 1;
//        int total = 0;
//        for (int i = Tryte_LENGTH - 1; i >= 0; i--) {
//            total += t * (trits[i].toInt());
//            t = 3 * t;
//        }
//        return total;
//    }
//
//    public static Tryte fromInt(int i) {
//        Tryte total = Tryte.zero();
//        boolean negative = i < 0;
//        if (negative) {
//            i = Math.abs(i);
//        }
//        for (int p = 0; p < 28; p++) {
//            if ((i & 1) == 1) {
//                total = total.add(PO2[p]);
//            }
//            i = i / 2;
//        }
//        if (negative) {
//            total = total.not();
//        }
//
//        return total;
//    }
//
//    // Value of method
//
//}
//
//
//
