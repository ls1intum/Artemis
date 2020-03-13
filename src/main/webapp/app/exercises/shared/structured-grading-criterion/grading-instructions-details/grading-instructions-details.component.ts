import { Component, OnInit, Input, ViewChild } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { UsageCountCommand } from 'app/shared/markdown-editor/domainCommands/usageCount.command';
import { CreditsCommand } from 'app/shared/markdown-editor/domainCommands/credits.command';
import { FeedbackCommand } from 'app/shared/markdown-editor/domainCommands/feedback.command';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingScaleCommand } from 'app/shared/markdown-editor/domainCommands/gradingScaleCommand';
import { GradingInstructionCommand } from 'app/shared/markdown-editor/domainCommands/gradingInstruction.command';
import { InstructionDescriptionCommand } from 'app/shared/markdown-editor/domainCommands/instructionDescription.command';
import { GradingCriterionCommand } from 'app/shared/markdown-editor/domainCommands/gradingCriterionCommand';
import { Exercise } from 'app/entities/exercise.model';
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
    exercise: Exercise;
    private instructions: GradingInstruction[];
    private criteria: GradingCriterion[];

    gradingCriterionCommand = new GradingCriterionCommand();
    gradingInstructionCommand = new GradingInstructionCommand();
    creditsCommand = new CreditsCommand();
    gradingScaleCommand = new GradingScaleCommand();
    instructionDescriptionCommand = new InstructionDescriptionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    domainCommands: DomainCommand[] = [
        this.creditsCommand,
        this.gradingScaleCommand,
        this.instructionDescriptionCommand,
        this.feedbackCommand,
        this.usageCountCommand,
        this.gradingCriterionCommand,
        this.gradingInstructionCommand,
    ];

    constructor() {}

    ngOnInit() {
        this.criteria = this.exercise.gradingCriteria;
        this.questionEditorText = this.generateMarkdown();
    }

    generateMarkdown(): string {
        let markdownText = '';
        if (this.criteria === undefined || this.criteria.length === 0) {
            this.criteria = [];
            const newCriteria = new GradingCriterion();
            this.criteria.push(newCriteria);
        }
        for (const criterion of this.criteria) {
            if (criterion.title === null || criterion.title === undefined) {
                // if it is a dummy criterion, leave out the command identifier
                markdownText += this.generateInstructionsMarkdown(criterion);
            } else {
                markdownText += '[gradingCriterion]' + criterion.title + '\n' + '\t' + this.generateInstructionsMarkdown(criterion);
            }
        }
        return markdownText;
    }

    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this grading instruction
     */
    generateInstructionsMarkdown(criterion: GradingCriterion): string {
        let markdownText = '';
        if (criterion.structuredGradingInstructions === undefined || criterion.structuredGradingInstructions.length === 0) {
            this.instructions = [];
            const newInstruction = new GradingInstruction();
            this.instructions.push(newInstruction);
            criterion.structuredGradingInstructions = this.instructions;
        }
        for (const instruction of criterion.structuredGradingInstructions) {
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
            instruction.instructionDescription = InstructionDescriptionCommand.text;
            return InstructionDescriptionCommand.identifier + ' ' + InstructionDescriptionCommand.text;
        }
        return InstructionDescriptionCommand.identifier + ' ' + instruction.instructionDescription;
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

    hasCriterionCommand(domainCommands: [string, DomainCommand][]): boolean {
        for (const [text, command] of domainCommands) {
            if (command instanceof GradingCriterionCommand) {
                return true;
            }
        }
        return false;
    }

    /**
     * @function createSubInstructionCommands
     * @desc 1. divides the input: domainCommands in two subarrays:
     *          instructionCommands, which consists of all stand-alone instructions
     *          criteriaCommands, which consists of instructions that belong to a criterion
     *       2. for each subarrray a method is called to create the criterion and instruction objects
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    createSubInstructionCommands(domainCommands: [string, DomainCommand][]): void {
        let instructionCommands;
        let criteriaCommands;
        let endOfInstructionsCommand = 0;
        if (this.hasCriterionCommand(domainCommands) === false) {
            this.setParentForInstructionsWithNoCriterion(domainCommands);
        } else {
            for (const [text, command] of domainCommands) {
                endOfInstructionsCommand++;
                if (command instanceof GradingCriterionCommand) {
                    instructionCommands = domainCommands.slice(0, endOfInstructionsCommand - 1);
                    if (instructionCommands.length !== 0) {
                        this.setParentForInstructionsWithNoCriterion(instructionCommands);
                    }
                    criteriaCommands = domainCommands.slice(endOfInstructionsCommand - 1);
                    if (criteriaCommands.length !== 0) {
                        this.instructions = []; // resets the instructions array to be filled with the instructions of the criteria
                        this.groupInstructionsToCriteria(criteriaCommands); // creates criterion object for each criterion and their corresponding instruction objects
                    }
                    break;
                }
            }
        }
    }
    /**
     * @function setParentForInstructionsWithNoCriterion
     * @desc 1. creates a dummy criterion object for each stand-alone instruction
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    setParentForInstructionsWithNoCriterion(domainCommands: [string, DomainCommand][]): void {
        for (const [text, command] of domainCommands) {
            if (command instanceof GradingInstructionCommand) {
                const dummyCriterion = new GradingCriterion();
                const newInstruction = new GradingInstruction();
                dummyCriterion.structuredGradingInstructions = [];
                dummyCriterion.structuredGradingInstructions.push(newInstruction);
                this.instructions.push(newInstruction);
                this.criteria.push(dummyCriterion);
            }
        }
        this.exercise.gradingCriteria = this.criteria;
        this.setInstructionParameters(domainCommands);
    }

    /**
     * @function groupInstructionsToCriteria
     * @desc 1. creates a criterion for each GradingCriterionCommandIdentifier
     *          and creates the instruction objects of this criterion then assigns them to their parent criterion
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    groupInstructionsToCriteria(domainCommands: [string, DomainCommand][]): void {
        if (this.exercise.gradingCriteria === undefined) {
            this.exercise.gradingCriteria = [];
        }
        for (const [text, command] of domainCommands) {
            if (command instanceof GradingCriterionCommand) {
                const newCriterion = new GradingCriterion();
                newCriterion.title = text;
                this.exercise.gradingCriteria.push(newCriterion);
                newCriterion.structuredGradingInstructions = [];
                const modifiedArray = domainCommands.slice(1); // remove GradingCriterionCommandIdentifier after creating its criterion object
                for (const [instrText, instrCommand] of modifiedArray) {
                    if (instrCommand instanceof GradingInstructionCommand) {
                        const newInstruction = new GradingInstruction(); // create instruction objects that belong to the above created criterion
                        newCriterion.structuredGradingInstructions.push(newInstruction);
                        this.instructions.push(newInstruction);
                    }
                    if (instrCommand instanceof GradingCriterionCommand) {
                        break;
                    }
                }
            }
        }
        this.setInstructionParameters(domainCommands.filter(([text, command]) => command instanceof GradingCriterionCommand === false));
    }

    /**
     * @function setInstructionParameters
     * @desc 1. Gets a tuple of text and domainCommandIdentifiers not including GradingCriterionCommandIdentifiers and assigns text values according to the domainCommandIdentifiers
     *       2. The tupple order is the same as the order of the commands in the markdown text inserted by the user
     *       instruction objects must be created before the method gets triggered
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    setInstructionParameters(domainCommands: [string, DomainCommand][]): void {
        let index = 0;
        for (const [text, command] of domainCommands) {
            if (command instanceof CreditsCommand) {
                this.instructions[index].credits = parseFloat(text);
            } else if (command instanceof GradingScaleCommand) {
                this.instructions[index].gradingScale = text;
            } else if (command instanceof InstructionDescriptionCommand) {
                this.instructions[index].instructionDescription = text;
            } else if (command instanceof FeedbackCommand) {
                this.instructions[index].feedback = text;
            } else if (command instanceof UsageCountCommand) {
                this.instructions[index].usageCount = parseInt(text, 10);
                index++; // index must be increased after the last parameter of the instruction to continue with the next instruction object
            }
        }
    }

    /**
     * @function domainCommandsFound
     * @desc 1. Gets a tuple of text and domainCommandIdentifiers and assigns text values according to the domainCommandIdentifiers
     *       2. The tupple order is the same as the order of the commands in the markdown text inserted by the user
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    domainCommandsFound(domainCommands: [string, DomainCommand][]): void {
        this.instructions = [];
        this.criteria = [];
        this.exercise.gradingCriteria = [];
        this.createSubInstructionCommands(domainCommands);
        console.log(this.exercise.gradingCriteria);
    }
}
