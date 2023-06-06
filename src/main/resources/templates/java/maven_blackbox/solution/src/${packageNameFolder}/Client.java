package ${packageName};

import ${packageName}.input.Command;
import ${packageName}.input.CommandParser;
import ${packageName}.input.InvalidCommandException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class Client {
    private static final String PROMPT = "sort> ";
    private static final Context context = new Context();
    // private static final Policy policy = new Policy(context);

    private Client() {
        throw new IllegalCallerException("utility class");
    }

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
        if (command instanceof Command.AddCommand addCommand) {
            context.addDates(addCommand.values());
        } else if (command instanceof Command.SortCommand) {
            // policy.configure();
            context.sort();
        } else if (command instanceof Command.ClearCommand) {
            context.clearDates();
        } else if (command instanceof Command.HelpCommand helpCommand) {
            System.out.println(helpCommand.helpMessage());
        } else if (command instanceof Command.PrintCommand) {
            System.out.println(context.getDates());
        } else if (command instanceof Command.QuitCommand) {
            return CommandRunResult.QUIT;
        } else {
            // can never happen since all cases of the sealed interface are
            // covered
            // ToDo: refactor with Java 21 switch expression patterns when
            //  released to let the compiler check exhaustivity
            throw new UnsupportedOperationException("Unknown command type.");
        }

        return CommandRunResult.CONTINUE;
    }

    private enum CommandRunResult {
        CONTINUE,
        QUIT
    }
}
