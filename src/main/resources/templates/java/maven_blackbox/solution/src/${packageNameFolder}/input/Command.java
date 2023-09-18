package ${packageName}.input;

import java.util.Date;
import java.util.List;

public sealed interface Command {
    /**
     * Represents an add command. This ensures that only a list of dates can be
     * added.
     *
     * @param dates The list of dates that is added to the list.
     */
    record AddCommand(List<Date> dates) implements Command { }

    /**
     * Represents a clear command.
     */
    record ClearCommand() implements Command { }

    /**
     * Reprents a help command.
     *
     * @param helpMessage The help message that is printed on the console.
     */
    record HelpCommand(String helpMessage) implements Command { }

    /**
     * Represents a print command.
     */
    record PrintCommand() implements Command { }

    /**
     * Represents a quit command.
     */
    record QuitCommand() implements Command { }

    /**
     * Represents a sort command.
     */
    record SortCommand() implements Command { }
}
