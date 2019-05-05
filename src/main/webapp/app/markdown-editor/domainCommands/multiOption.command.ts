import { DomainCommand } from 'app/markdown-editor/domainCommands';

export abstract class MultiOptionCommand extends DomainCommand {
    protected values: string[];
    setValues(values: string[]) {
        this.values = values;
    }
}
