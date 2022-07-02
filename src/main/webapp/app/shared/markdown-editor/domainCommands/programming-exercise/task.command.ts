import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';

export class TaskCommand extends DomainTagCommand {
    public static readonly identifier = '[task]';
    public static readonly taskPlaceholder = 'Task Short Description';
    public static readonly testCasePlaceholder = 'testCaseName';

    buttonTranslationString = 'artemisApp.programmingExercise.problemStatement.taskCommand';

    setEditor(aceEditor: any) {
        super.setEditor(aceEditor);

        const taskCommandCompleter = {
            getCompletions: (editor: any, session: any, pos: any, prefix: any, callback: any) => {
                callback(null, { caption: 'task', value: this.getTask(), meta: 'insert task' });
            },
        };
        this.addCompleter(taskCommandCompleter);
    }

    /**
     * The task structure is coupled to the value used in `ProgrammingExerciseTaskService` in the server and
     * `ProgrammingExerciseTaskExtensionWrapper` in the client
     * If you change the template, make sure to change it in all places!
     */
    private getTask() {
        return `${this.getOpeningIdentifier()}[${TaskCommand.taskPlaceholder}](${TaskCommand.testCasePlaceholder})`;
    }

    public getTagRegex(flags = ''): RegExp {
        const escapedOpeningIdentifier = escapeStringForUseInRegex(this.getOpeningIdentifier());
        return new RegExp(`${escapedOpeningIdentifier}(.*)`, flags);
    }

    /**
     * @function execute
     * @desc add a new task. doesn't use the closing identifier for legacy reasons.
     */
    execute(): void {
        const currentLine = this.getCurrentLine();
        const startingNumber = currentLine.match(/(\d+)\..*/);
        const thisLineNumber = startingNumber && startingNumber.length > 1 ? `\n${Number(startingNumber[1]) + 1}.` : '1.';
        const taskText = `${thisLineNumber} ${this.getTask()}`;
        this.moveCursorToEndOfRow();
        this.insertText(taskText);
        this.focus();
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
