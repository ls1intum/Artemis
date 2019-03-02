import { Command } from 'app/markdown-editor/commands/command';

export abstract class SpecialCommand extends Command {

    abstract getIdentifier(): string; // e.g. [-e]
}
