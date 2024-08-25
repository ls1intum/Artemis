import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { ValueItem } from 'app/shared/markdown-editor/action-constants';

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
