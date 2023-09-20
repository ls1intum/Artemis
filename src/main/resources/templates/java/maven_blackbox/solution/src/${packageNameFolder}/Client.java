package ${packageName};

import ${packageName}.input.Command;
import ${packageName}.input.CommandParser;
import ${packageName}.input.InvalidCommandException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class Client {
    private static final String PROMPT = "sort> ";
    private static final Context CONTEXT = new Context();

    private Client() {
        throw new IllegalCallerException("utility class");
    }

    /**
     * The entrypoint of the program.
     *
     * @param args The command line arguments.
     */
    public static void main(String[] args) throws IOException {
        BufferedReader in
                = new BufferedReader(new InputStreamReader(System.in));
        CommandRunResult commandRunResult = CommandRunResult.CONTINUE;
        while (commandRunResult == CommandRunResult.CONTINUE) {
            System.out.print(PROMPT);
            try {
                Command command = CommandParser.parseCommand(in.readLine());
                commandRunResult = runCommand(command);
            } catch (InvalidCommandException invalidCommandException) {
                System.out.println(invalidCommandException.getMessage());
            }
        }
    }

    private static CommandRunResult runCommand(final Command command) {
        switch (command) {
            case Command.AddCommand addCommand -> CONTEXT.addDates(addCommand.dates());
            case Command.SortCommand ignored -> CONTEXT.sort();
            case Command.ClearCommand ignored -> CONTEXT.clearDates();
            case Command.HelpCommand helpCommand -> System.out.println(helpCommand.helpMessage());
            case Command.PrintCommand ignored -> System.out.println(CONTEXT.getDates());
            case Command.QuitCommand ignored -> {
                return CommandRunResult.QUIT;
            }
        }

        return CommandRunResult.CONTINUE;
    }

    private enum CommandRunResult {
        CONTINUE,
        QUIT
    }
}
