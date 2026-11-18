package ${packageName}.input;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public final class CommandParser {

    private static final String HELP_MESSAGE =
                """
                add:   adds the given Dates to the list (format: YYYY-MM-DD)
                clear: empties the list
                help:  prints this text
                print: prints the list
                sort:  sorts the list
                quit:  quits the program""";

    private CommandParser() {
        throw new IllegalCallerException("utility class");
    }

    /**
     * Parses a line that was entered via the console. The first word of the
     * input determines the command, and possible arguments (if applicable)
     * can be appended, separated by whitespaces.
     *
     * @param inputLine The console input.
     * @return The corresponding command.
     * @throws InvalidCommandException If the command could not be parsed.
     */
    public static Command parseCommand(final String inputLine)
            throws InvalidCommandException {
        final String[] args = inputLine.strip().split("\\s+");

        if (args.length == 0) {
            throw new InvalidCommandException(
                    "Expected a command. Use 'help' to show available commands."
            );
        }

        final String commandVerb = args[0];
        return switch (commandVerb) {
            case "add" -> parseAddCommand(args);
            case "clear" -> new Command.ClearCommand();
            case "help" -> new Command.HelpCommand(HELP_MESSAGE);
            case "print" -> new Command.PrintCommand();
            case "quit" -> new Command.QuitCommand();
            case "sort" -> new Command.SortCommand();
            default -> throw new InvalidCommandException(
                    "Unknown command. Use 'help' to show available commands."
            );
        };
    }

    private static Command.AddCommand parseAddCommand(final String[] args)
            throws InvalidCommandException {
        if (args.length < 2) {
            throw new UnsupportedOperationException(
                    "The 'add' command needs some values that can be added."
            );
        }

        final List<Date> dates = new ArrayList<>();

        for (int i = 1; i < args.length; ++i) {
            final String arg = args[i];
            try {
                final Date date = parseDateInput(arg);
                dates.add(date);
            } catch (ParseException e) {
                throw new InvalidCommandException(
                        "Dates have to follow the format YYYY-MM-DD and must"
                                + " be valid."
                );
            }
        }

        return new Command.AddCommand(Collections.unmodifiableList(dates));
    }

    private static Date parseDateInput(final String input)
            throws ParseException {
        final SimpleDateFormat formatter
            = new SimpleDateFormat("yyyy-MM-dd");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        formatter.setLenient(false);
        return formatter.parse(input);
    }
}
