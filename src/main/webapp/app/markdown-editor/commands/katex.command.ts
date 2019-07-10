import { Command } from 'app/markdown-editor/commands/command';

/**
 * Insert a katex compatible formula.
 * Uses an e-function as the example.
 */
export class KatexCommand extends Command {
    buttonTranslationString = 'artemisApp.markdownEditor.commands.katex';
    execute(input?: string): void {
        const text = '$$ e^{\\frac{1}{4} y^2} $$';
        this.insertText(text);
    }
}
