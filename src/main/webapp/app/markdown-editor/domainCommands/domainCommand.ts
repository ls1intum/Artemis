import { Command } from 'app/markdown-editor/commands/command';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';

/** abstract class for all domainCommands - customized commands for ArTEMiS specific use cases
 * e.g multiple choice questons, drag an drop questions
 * Each domain command has its own logic and an unique identifier**/
export abstract class DomainCommand extends Command {
    abstract getOpeningIdentifier(): string; // e.g. [exp]
    abstract getClosingIdentifier(): string; // e.g. [/exp]

    /**
     * Generate a regex that can be used to get the content alone or including the tags.
     * index 0: Get content with tags around it.
     * index 1: Get content without the tags.
     */
    getTagRegex(): RegExp {
        const escapedOpeningIdentifier = escapeStringForUseInRegex(this.getOpeningIdentifier()),
            escapedClosingIdentifier = escapeStringForUseInRegex(this.getClosingIdentifier());
        return new RegExp(`${escapedOpeningIdentifier}(.*)${escapedClosingIdentifier}`, 'g');
    }

    /**
     * Checks if the cursor is placed within the identifiers of a domain command.
     * Returns the content between the identifiers if there is match, otherwise returns null.
     */
    isCursorWithinTag(): { matchStart: number; matchEnd: number; innerTagContent: string } | null {
        const { row, column } = this.aceEditorContainer.getEditor().getCursorPosition(),
            line = this.aceEditorContainer
                .getEditor()
                .getSession()
                .getLine(row),
            regex = this.getTagRegex();

        const indexes: Array<{ matchStart: number; matchEnd: number; innerTagContent: string }> = [];
        let match;
        // A line can have multiple tags in it, so we need to check for multiple matches.
        while ((match = regex.exec(line))) {
            indexes.push({ matchStart: match.index, matchEnd: match.index + match[0].length, innerTagContent: match[1] });
        }
        const matchOnCursor = indexes.find(({ matchStart, matchEnd }) => column > matchStart && column <= matchEnd);
        return matchOnCursor || null;
    }
}
