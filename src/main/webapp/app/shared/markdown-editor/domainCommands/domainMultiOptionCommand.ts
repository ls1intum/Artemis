import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';

export type ValueItem = {
    id: string;
    value: string;
};

/**
 * This domain command will be used as a dropdown in the markdown editor.
 */
export abstract class DomainMultiOptionCommand extends DomainCommand {
    protected values: ValueItem[] = [];

    // tslint:disable-next-line:completed-docs
    setValues(values: ValueItem[]) {
        this.values = values;
    }

    // tslint:disable-next-line:completed-docs
    getValues() {
        return this.values;
    }
}
