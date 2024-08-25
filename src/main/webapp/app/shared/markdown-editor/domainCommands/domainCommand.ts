import { Command } from 'app/shared/markdown-editor/commands/command';

/** abstract class for all domainCommands - customized commands for Artemis specific use cases
 * e.g. multiple choice questions, drag and drop questions
 * Each domain command has its own logic and a unique identifier**/
export abstract class DomainCommand extends Command {
    abstract getOpeningIdentifier(): string; // e.g. [exp]
    abstract getClosingIdentifier(): string; // e.g. [/exp]
}
