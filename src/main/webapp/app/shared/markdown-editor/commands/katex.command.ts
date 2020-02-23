import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

/**
 * Insert a katex compatible formula.
 * Uses an e-function as the example.
 */
export class KatexCommand extends DomainTagCommand {
    buttonIcon = 'equals';
    buttonTranslationString = 'artemisApp.markdownEditor.commands.katex';
    execute(input?: string): void {
        const text = `${this.getOpeningIdentifier()}e^{\\frac{1}{4} y^2}${this.getClosingIdentifier()}`;
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    getOpeningIdentifier(): string {
        return '$$ ';
    }

    getClosingIdentifier(): string {
        return ' $$';
    }
}
