import { Component, OnInit, Input, ViewChild } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { GradingCriteriaCommand } from 'app/shared/markdown-editor/domainCommands/gradingCriteria.command';
import { InstructionCommand } from 'app/shared/markdown-editor/domainCommands/instruction.command';
import { UsageCountCommand } from 'app/shared/markdown-editor/domainCommands/usageCount.command';
import { CreditsCommand } from 'app/shared/markdown-editor/domainCommands/credits.command';
import { FeedbackCommand } from 'app/shared/markdown-editor/domainCommands/feedback.command';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingScaleCommand } from 'app/shared/markdown-editor/domainCommands/gradingScaleCommand';
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
    gradingScaleCommand = new GradingScaleCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    domainCommands: DomainCommand[] = [
        this.katexCommand,
        this.creditsCommand,
        this.gradingScaleCommand,
        this.instructionCommand,
        this.feedbackCommand,
        this.usageCountCommand,
        this.gradingCriteriaCommand,
    ];

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

        if (this.instructions === undefined || this.instructions.length === 0) {
            this.instructions = [];
            const newInstruction = new GradingInstruction();
            this.instructions.push(newInstruction);
            this.criterion.structuredGradingInstructions = this.instructions;
        }
        for (const instruction of this.instructions) {
            markdownText +=
                '[gradingInstruction]' +
                '\n' +
                '\t' +
                this.generateCreditsText(instruction) +
                '\n' +
                '\t' +
                this.generateGradingScaleText(instruction) +
                '\n' +
                '\t' +
                this.generateInstructionDescriptionText(instruction) +
                '\n' +
                '\t' +
                this.generateInstructionFeedback(instruction) +
                '\n' +
                '\t' +
                this.generateUsageCount(instruction) +
                '\n' +
                '\n';
        }
        return markdownText;
    }

    generateCreditsText(instruction: GradingInstruction): string {
        if (instruction.credits === undefined) {
            instruction.credits = parseFloat(CreditsCommand.text);
            return CreditsCommand.identifier + ' ' + CreditsCommand.text;
        }
        return CreditsCommand.identifier + ' ' + instruction.credits;
    }
    generateGradingScaleText(instruction: GradingInstruction): string {
        if (instruction.gradingScale === undefined) {
            instruction.gradingScale = GradingScaleCommand.text;
            return GradingScaleCommand.identifier + ' ' + GradingScaleCommand.text;
        }
        return GradingScaleCommand.identifier + ' ' + instruction.gradingScale;
    }
    generateInstructionDescriptionText(instruction: GradingInstruction): string {
        if (instruction.instructionDescription === undefined) {
            instruction.instructionDescription = InstructionCommand.text;
            return InstructionCommand.identifier + ' ' + InstructionCommand.text;
        }
        return InstructionCommand.identifier + ' ' + instruction.instructionDescription;
    }

    generateInstructionFeedback(instruction: GradingInstruction): string {
        if (instruction.feedback === undefined) {
            instruction.feedback = FeedbackCommand.text;
            return FeedbackCommand.identifier + ' ' + FeedbackCommand.text;
        }
        return FeedbackCommand.identifier + ' ' + instruction.feedback;
    }

    generateUsageCount(instruction: GradingInstruction): string {
        if (instruction.usageCount === undefined) {
            instruction.usageCount = parseInt(UsageCountCommand.text, 10);
            return UsageCountCommand.identifier + ' ' + UsageCountCommand.text;
        }
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
        if (this.markdownEditor.gradingCriteriaCommandFired) {
            const newInstruction = new GradingInstruction();
            this.instructions.push(newInstruction);
            this.criterion.structuredGradingInstructions = this.instructions;
        }
        let index = 0;
        this.instructions = [];
        for (const [text, command] of domainCommands) {
            if (command instanceof CreditsCommand) {
                this.instructions[index].credits = parseFloat(text);
            } else if (command instanceof GradingScaleCommand) {
                this.instructions[index].gradingScale = text;
            } else if (command instanceof InstructionCommand) {
                this.instructions[index].instructionDescription = text;
            } else if (command instanceof FeedbackCommand) {
                this.instructions[index].feedback = text;
            } else if (command instanceof UsageCountCommand) {
                this.instructions[index].usageCount = parseInt(text, 10);
                index++; // index must be elevated after the last parameter of the instruction to continue with the next instruction object
            } else {
                const newInstruction = new GradingInstruction();
                this.instructions.push(newInstruction);
                continue;
            }
        }
        this.criterion.structuredGradingInstructions = this.instructions;
    }
}
