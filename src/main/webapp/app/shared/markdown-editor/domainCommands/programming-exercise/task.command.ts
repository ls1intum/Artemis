import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';

export class TaskCommand extends DomainTagCommand {
    public static readonly identifier = '[task]';
    public static readonly taskPlaceholder = 'Task Short Description';
    public static readonly testCasePlaceholder = 'testCaseName';
    public static readonly hintsPlaceholder = 'hintId1, hintId2';

    buttonTranslationString = 'artemisApp.programmingExercise.problemStatement.taskCommand';

    /**
     * Set the editor object and initialize a new task autocompleter. The autocompleter is registered at the editor.
     * @param aceEditor - Code editor object
     */
    setEditor(aceEditor: any) {
        super.setEditor(aceEditor);

        const taskCommandCompleter = {
            getCompletions: (editor: any, session: any, pos: any, prefix: any, callback: any) => {
                callback(null, { caption: 'task', value: this.getTask(), meta: 'insert task' });
            },
        };
        this.addCompleter(taskCommandCompleter);
    }

    private getTask() {
        return `${this.getOpeningIdentifier()}[${TaskCommand.taskPlaceholder}](${TaskCommand.testCasePlaceholder}){${TaskCommand.hintsPlaceholder}}`;
    }

    /**
     * Returns a regular expression, matching the task opening element.
     *
     * @param {string} flags - Configuration flags for the returned {RegExp} regular expression. Defaults to ''.
     * @returns {RegExp} Regular expression matching the task opening identifier
     */
    public getTagRegex(flags = ''): RegExp {
        const escapedOpeningIdentifier = escapeStringForUseInRegex(this.getOpeningIdentifier());
        return new RegExp(`${escapedOpeningIdentifier}(.*)`, flags);
    }

    /**
     * Add a new task, preceded by the the current line number, to the code editor.
     * Doesn't use the closing identifier for legacy reasons.
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

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return TaskCommand.identifier;
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return '[/task]';
    }
}
