import { Command } from 'app/shared/markdown-editor/commands/command';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';

/** abstract class for all domainCommands - customized commands for Artemis specific use cases
 * e.g. multiple choice questions, drag and drop questions
 * Each domain command has its own logic and a unique identifier**/
export abstract class DomainCommand extends Command {
    abstract getOpeningIdentifier(): string; // e.g. [exp]
    abstract getClosingIdentifier(): string; // e.g. [/exp]
    displayCommandButton = true;

    /**
     * Generate a regex that can be used to get the content alone or including the tags.
     * index 0: Get content with tags around it.
     * index 1: Get content without the tags.
     */
    getTagRegex(flags = ''): RegExp {
        const escapedOpeningIdentifier = escapeStringForUseInRegex(this.getOpeningIdentifier()),
            escapedClosingIdentifier = escapeStringForUseInRegex(this.getClosingIdentifier());
        return new RegExp(`${escapedOpeningIdentifier}(.*)${escapedClosingIdentifier}`, flags);
    }

    /**
     * Checks if the cursor is placed within the identifiers of a domain command.
     * Returns the content between the identifiers if there is match, otherwise returns null.
     */
    isCursorWithinTag(): { matchStart: number; matchEnd: number; innerTagContent: string } | null {
        const { row, column } = this.aceEditor.getCursorPosition(),
            line = this.aceEditor.getSession().getLine(row),
            regex = this.getTagRegex('g');

        const indexes: Array<{ matchStart: number; matchEnd: number; innerTagContent: string }> = [];

        // A line can have multiple tags in it, so we need to check for multiple matches.
        let match = regex.exec(line);
        while (match != undefined) {
            indexes.push({ matchStart: match.index, matchEnd: match.index + match[0].length, innerTagContent: match[1] });
            match = regex.exec(line);
        }
        const matchOnCursor = indexes.find(({ matchStart, matchEnd }) => column > matchStart && column <= matchEnd);
        return matchOnCursor || null;
    }

    /**
     * Checks if there is a tag in the line of the cursor.
     * Returns the content between the identifiers if there is match, otherwise returns null.
     */
    isTagInRow(row: number): { matchStart: number; matchEnd: number; innerTagContent: string } | null {
        const line = this.aceEditor.getSession().getLine(row),
            regex = this.getTagRegex();

        if (!line) {
            return null;
        }

        const match = line.match(regex);
        if (!match) {
            return null;
        }
        return { matchStart: match.index, matchEnd: match.index + match[0].length, innerTagContent: match[1] };
    }
}
