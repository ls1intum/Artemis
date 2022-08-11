import { Command } from 'app/shared/markdown-editor/commands/command';
import { ValueItem } from 'app/shared/markdown-editor/command-constants';

export abstract class MultiOptionCommand extends Command {
    protected values: ValueItem[] = [];

    getValues() {
        return this.values;
    }

    setValues(values: ValueItem[]) {
        this.values = values;
    }
}
