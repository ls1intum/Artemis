import { Command } from 'app/markdown-editor/commands/command';
import { DomainCommand } from 'app/markdown-editor/domainCommands';

/**
 * Insert a katex compatible formula.
 * Uses an e-function as the example.
 */
export class KatexCommand extends DomainCommand {
    buttonIcon = 'equals';
    buttonTranslationString = 'artemisApp.markdownEditor.commands.katex';
    execute(input?: string): void {
        const text = `${this.getOpeningIdentifier()}e^{\\frac{1}{4} y^2}${this.getClosingIdentifier()}`;
        this.insertText(text);
    }

    getClosingIdentifier(): string {
        return '$$ ';
    }

    getOpeningIdentifier(): string {
        return ' $$';
    }
}
