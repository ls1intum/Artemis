import { AceEditorComponent } from 'ng2-ace-editor';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';

export class TaskCommand extends DomainTagCommand {
    public static readonly identifier = '[task]';
    public static readonly taskPlaceholder = 'Task Short Description';
    public static readonly testCasePlaceholder = 'testCaseName';

    buttonTranslationString = 'arTeMiSApp.programmingExercise.problemStatement.taskCommand';

    setEditor(aceEditorContainer: AceEditorComponent) {
        super.setEditor(aceEditorContainer);

        const taskCommandCompleter = {
            getCompletions: (editor: any, session: any, pos: any, prefix: any, callback: any) => {
                callback(null, { caption: 'task', value: this.getTask(), meta: 'insert task' });
            },
        };
        this.addToCompleters(taskCommandCompleter);
    }

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
