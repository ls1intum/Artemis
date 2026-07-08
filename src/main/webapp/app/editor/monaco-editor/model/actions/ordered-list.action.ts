import { faListOl } from '@fortawesome/free-solid-svg-icons';
import { ListAction } from './list.action';

/**
 * Action used to add or modify a numbered list in the text editor.
 */
export class OrderedListAction extends ListAction {
    static readonly ID = 'numberedList.action';

    constructor() {
        super(OrderedListAction.ID, 'artemisApp.multipleChoiceQuestion.editor.orderedList', faListOl, undefined);
    }

    public getPrefix(lineNumber: number): string {
        const space = ' ';
        return `${lineNumber}.${space}`;
    }

    // Numbered lists derive each prefix from the line number in getPrefix(); the base class's abstract PREFIX is unused here.
    protected readonly PREFIX = '';
}
