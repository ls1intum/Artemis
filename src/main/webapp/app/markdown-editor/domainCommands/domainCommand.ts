import { Command } from 'app/markdown-editor/commands/command';

/** abstract class for all domainCommands - customized commands for ArTEMiS specific use cases
 * e.g multiple choice questons, drag an drop questions
 * Each domain command has its own logic and an unique identifier**/
export abstract class DomainCommand extends Command {

    abstract getOpeningIdentifier(): string; // e.g. [exp]
    abstract getClosingIdentifier(): string; // e.g. [/exp]
}
