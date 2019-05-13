import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';

export class TaskCommand extends DomainTagCommand {
    public static readonly identifier = '[task]';
    public static readonly taskPlaceholder = 'Task Short Description';
    public static readonly testCasePlaceholder = 'testCaseName';

    buttonTranslationString = 'arTeMiSApp.programmingExercise.problemStatement.taskCommand';

    /**
     * @function execute
     * @desc add a new task. doesn't use the closing identifier for legacy reasons.
     */
    execute(): void {
        const text = `\n${this.getOpeningIdentifier()}[${TaskCommand.taskPlaceholder}](${TaskCommand.testCasePlaceholder})`;
        this.insertText(text);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the task
     */
    getOpeningIdentifier(): string {
        return TaskCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the explanation
     */
    getClosingIdentifier(): string {
        return '[/task]';
    }
}
