import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';

export class TaskCommand extends DomainTagCommand {
    public static readonly identifier = '[task]';
    public static readonly taskPlaceholder = 'Task Short Description';
    public static readonly testCasePlaceholder = 'testCaseName';

    buttonTranslationString = 'arTeMiSApp.programmingExercise.problemStatement.taskCommand';

    /**
     * @function execute
     * @desc Add a new explanation to answer option or question title in the text editor at the location of the cursor
     */
    execute(): void {
        const text = `\n1. ${this.getOpeningIdentifier()}[${TaskCommand.taskPlaceholder}](${TaskCommand.testCasePlaceholder})`;
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
