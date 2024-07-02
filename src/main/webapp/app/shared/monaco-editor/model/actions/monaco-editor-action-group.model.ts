import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

export class MonacoEditorActionGroup<ActionType extends MonacoEditorAction> {
    translationKey: string;
    actions: ActionType[];
    icon?: IconDefinition;

    constructor(translationKey: string, action: ActionType[], icon?: IconDefinition) {
        this.translationKey = translationKey;
        this.actions = action;
        this.icon = icon;
    }
}
