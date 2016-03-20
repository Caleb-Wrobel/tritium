package not.the.mentalist.tritium.components;

import not.the.mentalist.tritium.units.Tryte;

import java.util.Deque;
import java.util.ArrayDeque;
/**
 *
 */


public enum OpStack {

    INSTANCE(){

        private Deque<Tryte> stack = new ArrayDeque<>();

        public void push(Tryte t) {
            stack.push(t);
        }

        public Tryte pop() {
            return stack.pop();
        }

    }
}
