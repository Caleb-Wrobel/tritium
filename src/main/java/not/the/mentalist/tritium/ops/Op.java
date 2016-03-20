package not.the.mentalist.tritium.ops;

import not.the.mentalist.tritium.units.Tryte;

/**
 * Created by Admin on 19-Mar-16.
 */
public interface Op {

    Tryte apply(Tryte instr);

    Tryte identifier();

    String form();

    String description();


}
