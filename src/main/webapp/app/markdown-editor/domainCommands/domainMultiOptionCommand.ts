import { DomainCommand } from 'app/markdown-editor/domainCommands';

export type ValueItem = {
    id: string;
    value: string;
};

/**
 * This domain command will be used as a dropdown in the markdown editor.
 */
export abstract class DomainMultiOptionCommand extends DomainCommand {
    protected values: ValueItem[] = [];
    setValues(values: ValueItem[]) {
        this.values = values;
    }
    getValues() {
        return this.values;
    }
}
