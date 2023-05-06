package ${packageName}.input;

import java.util.Date;
import java.util.List;

public sealed interface Command {
    record AddCommand(List<Date> values) implements Command {}
    record ClearCommand() implements Command {}
    record HelpCommand() implements Command {}
    record PrintCommand() implements Command {}
    record QuitCommand() implements Command {}
    record SortCommand() implements Command {}
}
