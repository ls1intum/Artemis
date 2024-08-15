import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { TextWithDomainAction } from './markdown-editor-monaco.component';

// TODO update docs
export function parseMarkdownForDomainActions(markdown: string, domainActions: MonacoEditorDomainAction[]): TextWithDomainAction[] {
    let remainingText = markdown;
    const actionIdentifiersString = domainActions
        .map((action) => action.getOpeningIdentifier())
        .map((identifier) => identifier.replace('[', '').replace(']', ''))
        .map(escapeStringForUseInRegex)
        .join('|');
    const actionTextsMappedToActionIdentifiers: TextWithDomainAction[] = [];
    /** create a new regex expression which searches for the domainCommands identifiers
     * (?=   If a command is found, add the command identifier to the result of the split
     * \\[  look for the character '[' to determine the beginning of the command identifier
     * (${commandIdentifiersString}) look if after the '[' one of the element of commandIdentifiersString is contained
     * ] look for the character ']' to determine the end of the command identifier
     * )  close the bracket
     *  g: search in the whole string
     *  i: case insensitive, neglecting capital letters
     *  m: match the regex over multiple lines*/
    const regex = new RegExp(`(?=\\[(${actionIdentifiersString})])`, 'gmi');
    while (remainingText.length) {
        const [textWithActionIdentifier] = remainingText.split(regex, 1);
        remainingText = remainingText.substring(textWithActionIdentifier.length);
        const commandTextWithCommandIdentifier = parseLineForDomainCommand(textWithActionIdentifier.trim(), domainActions);
        actionTextsMappedToActionIdentifiers.push(commandTextWithCommandIdentifier);
    }

    return actionTextsMappedToActionIdentifiers;
}

/**
 * @function parseLineForDomainCommand
 * @desc Couple each text with the domainCommandIdentifier to emit that to the parent component for the value assignment
 *       1. Check which domainCommand identifier is contained within the text
 *       2. Remove the domainCommand identifier from the text
 *       3. Create an array with first element text and second element the domainCommand TODO UPDATE DOCS (command -> action) identifier
 * @param text {string} from the parse function
 * @param domainActions The array of domain actions for which to extract the identifiers
 * @return array of the text with the domainCommand identifier
 */
function parseLineForDomainCommand(text: string, domainActions: MonacoEditorDomainAction[]): TextWithDomainAction {
    for (const domainAction of domainActions) {
        const possibleOpeningCommandIdentifier = [
            domainAction.getOpeningIdentifier(),
            domainAction.getOpeningIdentifier().toLowerCase(),
            domainAction.getOpeningIdentifier().toUpperCase(),
        ];
        if (possibleOpeningCommandIdentifier.some((identifier) => text.indexOf(identifier) !== -1)) {
            const trimmedLineWithoutIdentifier = possibleOpeningCommandIdentifier.reduce((line, identifier) => line.replace(identifier, ''), text).trim();
            return [trimmedLineWithoutIdentifier, domainAction];
        }
    }
    return [text.trim(), undefined];
}
