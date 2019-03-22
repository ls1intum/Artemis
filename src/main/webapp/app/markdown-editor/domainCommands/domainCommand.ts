import { Command } from 'app/markdown-editor/commands/command';

/** abstract class for all domainCommands - customized commands for ArTEMiS specific use cases
 * e.g multiple choice questons, drag an drop questions **/
export abstract class DomainCommand extends Command {

    abstract getIdentifier(): string; // e.g. [-e]
}
