import { Command } from 'app/markdown-editor/commands/command';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';

/** abstract class for all domainCommands - customized commands for ArTEMiS specific use cases
 * e.g multiple choice questons, drag an drop questions
 * Each domain command has its own logic and an unique identifier**/
export abstract class DomainCommand extends Command {
    abstract getOpeningIdentifier(): string; // e.g. [exp]
    abstract getClosingIdentifier(): string; // e.g. [/exp]

    /**
     * Checks if the cursor is placed within the identifiers of a domain command.
     * Returns the content between the identifiers if there is match, otherwise returns null.
     */
    isCursorWithinTag() {
        const { row, column } = this.aceEditorContainer.getEditor().getCursorPosition(),
            line = this.aceEditorContainer
                .getEditor()
                .getSession()
                .getLine(row),
            escapedOpeningIdentifier = escapeStringForUseInRegex(this.getOpeningIdentifier()),
            escapedClosingIdentifier = escapeStringForUseInRegex(this.getClosingIdentifier()),
            matchRegex = new RegExp(`${escapedOpeningIdentifier}.*${escapedClosingIdentifier}`, 'g'),
            extractRegex = new RegExp(`${escapedOpeningIdentifier}(.*)${escapedClosingIdentifier}`);

        let match,
            indexes: Array<[number, number, string]> = [];
        while ((match = matchRegex.exec(line))) {
            indexes.push([match.index, match.index + match[0].length, match[0].toString()]);
        }
        const matchOnCursor = indexes.find(([start, end]) => column > start && column < end);
        return (matchOnCursor && matchOnCursor[2] && matchOnCursor[2].match(extractRegex)[1]) || null;
    }
}
