import { Component, OnInit, ViewChild } from '@angular/core';
import { CreditsCommand, DomainCommand, FeedbackCommand, GradingCriteriaCommand, InstructionCommand, UsageCountCommand } from 'app/markdown-editor/domainCommands';
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
    @ViewChild('markdownEditor', { static: false })
    private markdownEditor: MarkdownEditorComponent;

    katexCommand = new KatexCommand();
    gradingCriteriaCommand = new GradingCriteriaCommand();
    creditsCommand = new CreditsCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    domainCommands: DomainCommand[] = [this.katexCommand, this.creditsCommand, this.instructionCommand, this.feedbackCommand, this.usageCountCommand, this.gradingCriteriaCommand];
    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {
        this.questionEditorText = this.generateMarkdown();
    }
    showStructuredGradingInstructionsPreview = true;

    private inputArr: [string, DomainCommand][];
    credits = new Array();
    instructions = new Array();
    feedback = new Array();
    usageCount = new Array();
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
        this.credits = [];
        this.instructions = [];
        this.feedback = [];
        this.usageCount = [];
        for (const [text, command] of domainCommands) {
            if (command instanceof CreditsCommand) {
                this.credits.push(text);
            } else if (command instanceof InstructionCommand) {
                this.instructions.push(text);
            } else if (command instanceof FeedbackCommand) {
                this.feedback.push(text);
            } else {
                this.usageCount.push(text);
            }
        }
    }
}
