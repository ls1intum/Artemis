import { DomainCommand } from 'app/markdown-editor/domainCommands';

/**
 * This domain command will be used as a dropdown in the markdown editor.
 */
export abstract class MultiOptionCommand extends DomainCommand {
    protected values: string[];
    setValues(values: string[]) {
        this.values = values;
    }
}
