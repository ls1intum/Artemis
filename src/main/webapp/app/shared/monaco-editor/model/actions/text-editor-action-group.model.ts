import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';

export class TextEditorActionGroup<ActionType extends TextEditorAction> {
    translationKey: string;
    actions: ActionType[];
    icon?: IconDefinition;

    constructor(translationKey: string, action: ActionType[], icon?: IconDefinition) {
        this.translationKey = translationKey;
        this.actions = action;
        this.icon = icon;
    }
}
