import { MonacoEditorDomainAction } from './monaco-editor-domain-action.model';
import { ValueItem } from 'app/shared/markdown-editor/command-constants';

export interface DomainActionWithOptionsArguments {
    selectedItem: ValueItem;
}

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
