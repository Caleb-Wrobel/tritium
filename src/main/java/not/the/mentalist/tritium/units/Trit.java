package not.the.mentalist.tritium.units;

/**
 * Ternary Digit Enumerated Type
 *
 * <P>Values of balanced ternary digits with arithmetic and
 * logical operations.
 *
 * <P>Balanced ternary digits may have values of 0, 1, or -1.
 * Here these values are represented with constants with names
 * O, I, and T, respectively.
 *
 * <P>Operations are implemented in the enum instances
 * themselves and methods on the type Trit are can be used as
 * Polish notation like expressions
 *
 * @author Caleb
 * @version 0.1
 */
public enum Trit implements Comparable<Trit>{
    O() {
        @Override
        public Trit[] add(Trit that) {
            switch (that){
                case O: return oo;
                case I: return oi;
                case T: return ot;
                default: return oo;
            }
        }

        @Override
        protected Trit[] add(Trit[] b) {
            return new Trit[0];
        }

        @Override
        public Trit not() { return O; }

        @Override
        public Trit[] sub(Trit that) {
            return this.add(that.not());
        }

        @Override
        public Trit and(Trit that) {
            switch (that){
                case O: return O;
                case I: return O;
                case T: return T;
                default: return O;
            }
        }

        @Override
        public Trit or(Trit that) {
            switch (that){
                case O: return O;
                case I: return I;
                case T: return O;
                default: return O;
            }
        }

        @Override
        public char toChar() {
            return 'O';
        }
    },
    I() {
        @Override
        public Trit[] add(Trit that) {
            switch (that){
                case O: return oi;
                case I: return it;
                case T: return oo;
                default: return oo;
            }
        }

        @Override
        protected Trit[] add(Trit[] b) {
            return new Trit[0];
        }

        @Override
        public Trit[] sub(Trit that) {
            return this.add(that.not());
        }

        @Override
        public Trit and(Trit that) {
            return that;
        }

        @Override
        public Trit or(Trit that) {
            return I;
        }

        @Override
        public Trit not() {
            return T;
        }

        @Override
        public char toChar() {
            return 'I';
        }


    },
    T() {
        @Override
        public Trit[] add(Trit that) {
            switch (that){
                case O: return ot;
                case I: return oo;
                case T: return ti;
                default: return oo;
            }
        }

        @Override
        protected Trit[] add(Trit[] b) {
            return new Trit[0];
        }

        @Override
        public Trit[] sub(Trit that) {
            return this.add(that.not());
        }

        @Override
        public Trit and(Trit that) {
            return T;
        }

        @Override
        public Trit or(Trit that) {
            return that;
        }

        @Override
        public Trit not() {
            return I;
        }

        @Override
        public char toChar() {
            return 'T';
        }
    };

    abstract public Trit[] add(Trit that);
    abstract protected Trit[] add(Trit[] b);
    abstract public Trit[] sub(Trit that);
    abstract public Trit and(Trit that);
    abstract public Trit or(Trit that);
    abstract public Trit not();
    abstract public char toChar();


    // Convenience names for two digit results
    final static Trit[] tt = {Trit.T,Trit.T};
    final static Trit[] to = {Trit.T,Trit.O};
    final static Trit[] ti = {Trit.T,Trit.I};
    final static Trit[] ot = {Trit.O,Trit.T};
    final static Trit[] oo = {Trit.O,Trit.O};
    final static Trit[] oi = {Trit.O,Trit.I};
    final static Trit[] it = {Trit.I,Trit.T};
    final static Trit[] io = {Trit.I,Trit.O};
    final static Trit[] ii = {Trit.I,Trit.I};

    public static Trit[] triAdd(Trit a, Trit b, Trit c){
        Trit[] n = a.add(b);
        Trit[] m = n[1].add(c);
        Trit[] p = n[0].add(m[0]);


        return new Trit[]{p[1],m[1]};
    }

    public static Trit[] add() { return oo; }


}
