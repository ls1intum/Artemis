import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { TextWithDomainAction } from './markdown-editor-monaco.component';

/**
 * Searches for the domain actions in the given markdown text and returns the text split into parts, each part associated with the domain action it belongs to.
 * Note that the text will be trimmed before being returned.
 * @param markdown The markdown text to parse
 * @param domainActions The domain actions to search for in the markdown
 */
export function parseMarkdownForDomainActions(markdown: string, domainActions: MonacoEditorDomainAction[]): TextWithDomainAction[] {
    let remainingText = markdown;
    const actionIdentifiersString = domainActions
        .map((action) => action.getOpeningIdentifier())
        .map((identifier) => identifier.replace('[', '').replace(']', ''))
        .map(escapeStringForUseInRegex)
        .join('|');
    const textMappedToActionIdentifiers: TextWithDomainAction[] = [];

    /*
     * The following regex is used to split the text into parts, each part associated with the domain action it belongs to. It is structured as follows:
     * 1. (?=   If an action is found, add the action identifier to the result of the split
     * 2. \\[  look for the character '[' to determine the beginning of the action identifier
     * 3. (${actionIdentifiersString}) look if after the '[' one of the element of actionIdentifiersString is contained
     * 4. ] look for the character ']' to determine the end of the action identifier
     * 5. ) ends the group
     * Flags:
     * - g: search in the whole string
     * - m: match the regex over multiple lines
     * - i: case-insensitive matching
     */
    const regex = new RegExp(`(?=\\[(${actionIdentifiersString})])`, 'gmi');
    while (remainingText.length) {
        const [textWithActionIdentifier] = remainingText.split(regex, 1);
        remainingText = remainingText.substring(textWithActionIdentifier.length);
        const textWithDomainAction = parseLineForDomainAction(textWithActionIdentifier.trim(), domainActions);
        textMappedToActionIdentifiers.push(textWithDomainAction);
    }

    return textMappedToActionIdentifiers;
}

/**
 * Checks if the given line contains any of the domain action identifiers and returns the text without the identifier along with the domain action it belongs to.
 * @param text The text to parse
 * @param domainActions The domain actions to search for in the text
 */
function parseLineForDomainAction(text: string, domainActions: MonacoEditorDomainAction[]): TextWithDomainAction {
    for (const domainAction of domainActions) {
        const possibleOpeningIdentifiers = [
            domainAction.getOpeningIdentifier(),
            domainAction.getOpeningIdentifier().toLowerCase(),
            domainAction.getOpeningIdentifier().toUpperCase(),
        ];
        if (possibleOpeningIdentifiers.some((identifier) => text.indexOf(identifier) !== -1)) {
            const trimmedLineWithoutIdentifier = possibleOpeningIdentifiers.reduce((line, identifier) => line.replace(identifier, ''), text).trim();
            return { text: trimmedLineWithoutIdentifier, action: domainAction };
        }
    }
    return { text: text.trim() };
}
