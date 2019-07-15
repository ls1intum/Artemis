import 'brace/mode/latex';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { InitializableCommand } from 'app/markdown-editor/domainCommands/Initializable.command';

/**
 * Insert a katex compatible formula.
 * Uses an e-function as the example.
 */
export class KatexCommand extends DomainTagCommand implements InitializableCommand {
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

    initializeEditor = () => {
        this.aceEditorContainer
            .getEditor()
            .getSession()
            .setMode('ace/mode/latex');
    };
}
