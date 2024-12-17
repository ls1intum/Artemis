import { faListUl } from '@fortawesome/free-solid-svg-icons';
import { ListAction } from './list.action';

const BULLET_PREFIX = '- ';

/**
 * Action used to add or modify a bullet-point list in the text editor.
 */
export class BulletedListAction extends ListAction {
    static readonly ID = 'bulletedList.action';

    protected readonly PREFIX = BULLET_PREFIX;

    constructor() {
        super(BulletedListAction.ID, 'artemisApp.multipleChoiceQuestion.editor.unorderedList', faListUl, undefined);
    }

    protected getPrefix(lineNumber: number): string {
        void lineNumber;
        return this.PREFIX;
    }
}
