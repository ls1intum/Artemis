import { MonacoEditorDomainAction } from './monaco-editor-domain-action.model';
import { ValueItem } from 'app/shared/markdown-editor/value-item.model';

export interface DomainActionWithOptionsArguments {
    selectedItem: ValueItem;
}

/**
 * Class representing domain actions for Artemis-specific use cases with options. The user can select an item from a list of options.
 */
export abstract class MonacoEditorDomainActionWithOptions extends MonacoEditorDomainAction {
    values: ValueItem[] = [];

    setValues(values: ValueItem[]) {
        this.values = values;
    }

    getValues(): ValueItem[] {
        return this.values;
    }

    executeInCurrentEditor(args?: DomainActionWithOptionsArguments) {
        super.executeInCurrentEditor(args);
    }
}
