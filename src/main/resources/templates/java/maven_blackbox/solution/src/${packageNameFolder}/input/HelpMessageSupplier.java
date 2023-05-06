package ${packageName}.input;

import java.util.function.Supplier;

public class HelpMessageSupplier implements Supplier<String> {
    @Override
    public String get() {
        return """
                add:   adds the given Dates to the list (format: YYYY-MM-DD)
                clear: empties the list
                help:  prints this text
                print: prints the list
                sort:  sorts the list
                quit:  quits the program""";
    }
}
