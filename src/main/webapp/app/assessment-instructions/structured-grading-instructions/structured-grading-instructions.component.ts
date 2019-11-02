import { Component, OnInit } from '@angular/core';
import {
    CorrectOptionCommand,
    CreditsCommand,
    DomainCommand,
    ExplanationCommand,
    FeedbackCommand,
    HintCommand,
    IncorrectOptionCommand,
    InstructionCommand,
    UsageCountCommand,
} from 'app/markdown-editor/domainCommands';
import { KatexCommand } from 'app/markdown-editor/commands';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { MarkdownEditorComponent } from 'app/markdown-editor';

@Component({
    selector: 'jhi-structured-grading-instructions',
    templateUrl: './structured-grading-instructions.component.html',
    styleUrls: ['./structured-grading-instructions.scss'],
})
export class StructuredGradingInstructionsComponent implements OnInit {
    /** Ace Editor configuration constants **/
    questionEditorText = '';

    private markdownEditor: MarkdownEditorComponent;

    katexCommand = new KatexCommand();
    creditsCommand = new CreditsCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCount = new UsageCountCommand();

    domainCommands: DomainCommand[] = [this.katexCommand, this.creditsCommand, this.instructionCommand, this.feedbackCommand, this.usageCount];
    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {
        this.questionEditorText = this.generateMarkdown();
    }
    showStructuredGradingInstructionsPreview = true;
    instruction: any;
    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this question
     * 1.instruction score, criteria,feedback and usage count are added using ArtemisMarkdown
     * 2.
     *
     */
    generateMarkdown(): string {
        const markdownText =
            CreditsCommand.identifier +
            ' ' +
            CreditsCommand.text +
            '\n' +
            InstructionCommand.identifier +
            ' ' +
            InstructionCommand.text +
            '\n' +
            FeedbackCommand.identifier +
            ' ' +
            FeedbackCommand.text +
            '\n' +
            UsageCountCommand.identifier +
            ' ' +
            UsageCountCommand.text;
        return markdownText;
    }
    prepareForSave(): void {
        this.markdownEditor.parse();
    }
    /**
     * @function domainCommandsFound
     * @desc todo
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    domainCommandsFound(domainCommands: [string, DomainCommand][]): void {
        for (const [text, command] of domainCommands) {
            if (command instanceof CreditsCommand) {
                // this.instruction.score = text;
            } else if (command instanceof InstructionCommand) {
            } else if (command instanceof FeedbackCommand) {
            } else {
            }
        }
    }
}
