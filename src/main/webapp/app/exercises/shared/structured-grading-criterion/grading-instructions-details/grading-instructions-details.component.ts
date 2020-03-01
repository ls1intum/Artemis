import { Component, OnInit, Input, ViewChild } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-instruction/grading-instruction.model';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { GradingCriteriaCommand } from 'app/shared/markdown-editor/domainCommands/gradingCriteria.command';
import { InstructionCommand } from 'app/shared/markdown-editor/domainCommands/instruction.command';
import { UsageCountCommand } from 'app/shared/markdown-editor/domainCommands/usageCount.command';
import { CreditsCommand } from 'app/shared/markdown-editor/domainCommands/credits.command';
import { FeedbackCommand } from 'app/shared/markdown-editor/domainCommands/feedback.command';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
@Component({
    selector: 'jhi-grading-instructions-details',
    templateUrl: './grading-instructions-details.component.html',
})
export class GradingInstructionsDetailsComponent implements OnInit {
    /** Ace Editor configuration constants **/
    questionEditorText = '';
    @ViewChild('markdownEditor', { static: false })
    private markdownEditor: MarkdownEditorComponent;
    @Input()
    criterion: GradingCriterion;
    private instructions: GradingInstruction[];
    katexCommand = new KatexCommand();
    gradingCriteriaCommand = new GradingCriteriaCommand();
    creditsCommand = new CreditsCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    domainCommands: DomainCommand[] = [this.katexCommand, this.creditsCommand, this.instructionCommand, this.feedbackCommand, this.usageCountCommand, this.gradingCriteriaCommand];

    constructor() {}

    ngOnInit() {
        this.instructions = this.criterion.structuredGradingInstructions;
        this.questionEditorText = this.generateMarkdown();
    }
    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this grading instruction
     */
    generateMarkdown(): string {
        let markdownText = '';
        if (this.instructions !== undefined && this.instructions.length !== 0) {
            for (let instruction of this.instructions) {
                markdownText +=
                    this.generateCreditsText(instruction) +
                    '\n' +
                    this.generateInstructionDescriptionText(instruction) +
                    '\n' +
                    this.generateInstructionFeedback(instruction) +
                    '\n' +
                    this.generateUsageCount(instruction) +
                    '\n' +
                    '\n';
            }
        } else {
            markdownText =
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
        }
        return markdownText;
    }

    generateCreditsText(instruction: GradingInstruction): string {
        return CreditsCommand.identifier + ' ' + instruction.credit;
    }

    generateInstructionDescriptionText(instruction: GradingInstruction): string {
        return InstructionCommand.identifier + ' ' + instruction.instructionDescription;
    }

    generateInstructionFeedback(instruction: GradingInstruction): string {
        return FeedbackCommand.identifier + ' ' + instruction.feedback;
    }

    generateUsageCount(instruction: GradingInstruction): string {
        return UsageCountCommand.identifier + ' ' + instruction.usageCount;
    }

    prepareForSave(): void {
        this.markdownEditor.parse();
    }
    /**
     * @function domainCommandsFound
     * @desc 1. Gets a tuple of text and domainCommandIdentifiers and assigns text values according to the domainCommandIdentifiers
     *       2. The tupple order is the same as the order of the commands in the markdown text inserted by the user
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    domainCommandsFound(domainCommands: [string, DomainCommand][]): void {
        let index = 0;
        for (const [text, command] of domainCommands) {
            if (command instanceof CreditsCommand) {
                this.instructions[index].credit = parseFloat(text);
            } else if (command instanceof InstructionCommand) {
                this.instructions[index].instructionDescription = text;
            } else if (command instanceof FeedbackCommand) {
                this.instructions[index].feedback = text;
            } else {
                this.instructions[index].usageCount = parseInt(text, 10);
            }
            index++;
        }
    }
}
