import { Component, OnInit, Input, ViewChildren, QueryList, ChangeDetectorRef, AfterViewInit } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
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
import { cloneDeep } from 'lodash';

@Component({
    selector: 'jhi-grading-instructions-details',
    templateUrl: './grading-instructions-details.component.html',
    styleUrls: ['./grading-instructions-details.component.scss'],
})
export class GradingInstructionsDetailsComponent implements OnInit, AfterViewInit {
    /** Ace Editor configuration constants **/
    questionEditorText = '';
    @ViewChildren('markdownEditor')
    private markdownEditors: QueryList<MarkdownEditorComponent>;
    @Input()
    exercise: Exercise;
    private instructions: GradingInstruction[];
    private criteria: GradingCriterion[];

    backupExercise: Exercise;

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

    domainCommandsGradingInstructions: DomainCommand[] = [
        this.creditsCommand,
        this.gradingScaleCommand,
        this.instructionDescriptionCommand,
        this.feedbackCommand,
        this.usageCountCommand,
    ];

    constructor(private changeDetector: ChangeDetectorRef) {}

    ngOnInit() {
        this.criteria = this.exercise.gradingCriteria || [];
        this.backupExercise = cloneDeep(this.exercise);
        this.questionEditorText = this.generateMarkdown();
    }

    ngAfterViewInit() {
        if (this.exercise.gradingInstructionFeedbackUsed) {
            this.initializeMarkdown();
        }
    }

    initializeMarkdown() {
        let index = 0;
        this.changeDetector.detectChanges();
        this.criteria!.forEach((criteria) => {
            criteria.structuredGradingInstructions.forEach((instruction) => {
                this.markdownEditors.get(index)!.markdownTextChange(this.generateInstructionText(instruction));
                index += 1;
            });
        });
    }

    generateMarkdown(): string {
        let markdownText = '';
        if (this.criteria === undefined || this.criteria.length === 0) {
            this.criteria = [];
            const dummyCriterion = new GradingCriterion();
            const exampleCriterion = new GradingCriterion();
            exampleCriterion.title = 'This is an Example criterion';
            const exampleInstr = new GradingInstruction();
            exampleCriterion.structuredGradingInstructions = [];
            exampleCriterion.structuredGradingInstructions.push(exampleInstr);
            exampleCriterion.structuredGradingInstructions.push(exampleInstr); // to showcase that a criterion consists of multiple instructions
            this.criteria.push(dummyCriterion);
            this.criteria.push(exampleCriterion);
        }
        for (const criterion of this.criteria) {
            if (criterion.title === null || criterion.title === undefined) {
                // if it is a dummy criterion, leave out the command identifier
                markdownText += this.generateInstructionsMarkdown(criterion);
            } else {
                markdownText += GradingCriterionCommand.identifier + criterion.title + '\n' + '\t' + this.generateInstructionsMarkdown(criterion);
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
            markdownText += this.generateInstructionText(instruction);
        }
        return markdownText;
    }

    generateInstructionText(instruction: GradingInstruction): string {
        let markdownText = '';
        markdownText =
            GradingInstructionCommand.identifier +
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
        this.markdownEditors.forEach((component) => {
            component.parse();
        });
    }

    hasCriterionCommand(domainCommands: [string, DomainCommand | null][]): boolean {
        return domainCommands.some(([, command]) => command instanceof GradingCriterionCommand);
    }

    /**
     * @function createSubInstructionCommands
     * @desc 1. divides the input: domainCommands in two subarrays:
     *          instructionCommands, which consists of all stand-alone instructions
     *          criteriaCommands, which consists of instructions that belong to a criterion
     *       2. for each subarrray a method is called to create the criterion and instruction objects
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    createSubInstructionCommands(domainCommands: [string, DomainCommand | null][]): void {
        let instructionCommands;
        let criteriaCommands;
        let endOfInstructionsCommand = 0;
        if (!this.hasCriterionCommand(domainCommands)) {
            this.setParentForInstructionsWithNoCriterion(domainCommands);
        } else {
            for (const [, command] of domainCommands) {
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
    setParentForInstructionsWithNoCriterion(domainCommands: [string, DomainCommand | null][]): void {
        for (const [, command] of domainCommands) {
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
    groupInstructionsToCriteria(domainCommands: [string, DomainCommand | null][]): void {
        const initialCriteriaCommands = domainCommands;
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
                let endOfCriterion = 0;
                for (const [, instrCommand] of modifiedArray) {
                    endOfCriterion++;
                    if (instrCommand instanceof GradingInstructionCommand) {
                        const newInstruction = new GradingInstruction(); // create instruction objects that belong to the above created criterion
                        newCriterion.structuredGradingInstructions.push(newInstruction);
                        this.instructions.push(newInstruction);
                    }
                    if (instrCommand instanceof GradingCriterionCommand) {
                        domainCommands = domainCommands.slice(endOfCriterion, domainCommands.length);
                        break;
                    }
                }
            }
        }
        this.setInstructionParameters(initialCriteriaCommands.filter(([, command]) => !(command instanceof GradingCriterionCommand)));
    }

    /**
     * @function setInstructionParameters
     * @desc 1. Gets a tuple of text and domainCommandIdentifiers not including GradingCriterionCommandIdentifiers and assigns text values according to the domainCommandIdentifiers
     *       2. The tupple order is the same as the order of the commands in the markdown text inserted by the user
     *       instruction objects must be created before the method gets triggered
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */

    setInstructionParameters(domainCommands: [string, DomainCommand | null][]): void {
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
    domainCommandsFound(domainCommands: [string, DomainCommand | null][]): void {
        this.instructions = [];
        this.criteria = [];
        this.exercise.gradingCriteria = [];
        this.createSubInstructionCommands(domainCommands);
    }

    /**
     * @function onInstructionChange
     * @desc 1. Gets a tuple of text and domainCommandIdentifiers and assigns text values according to the domainCommandIdentifiers
     *       2. The tupple order is the same as the order of the commands in the markdown text inserted by the user
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     * @param {GradingInstruction} instruction
     * @param {GradingCriterion} criteria
     */
    onInstructionChange(domainCommands: [string, DomainCommand | null][], instruction: GradingInstruction): void {
        this.instructions = [];
        this.instructions.push(instruction);
        this.setInstructionParameters(domainCommands);
    }

    /**
     * @function resetInstruction
     * @desc Resets the whole instruction
     * @param instruction {GradingInstruction} the instruction, which will be reset
     * @param criteria {GradingCriterion} the criteria, which includes the instruction that will be reset
     */
    resetInstruction(instruction: GradingInstruction, criteria: GradingCriterion) {
        const criteriaIndex = this.findCriteriaIndex(criteria, this.exercise);
        const backupCriteriaIndex = this.findCriteriaIndex(criteria, this.backupExercise);

        if (backupCriteriaIndex >= 0) {
            const instructionIndex = this.findInstructionIndex(instruction, this.exercise, criteriaIndex);
            const backupInstructionIndex = this.findInstructionIndex(instruction, this.backupExercise, backupCriteriaIndex);

            if (backupInstructionIndex >= 0) {
                this.exercise.gradingCriteria![criteriaIndex].structuredGradingInstructions![instructionIndex] = cloneDeep(
                    this.backupExercise.gradingCriteria![backupCriteriaIndex].structuredGradingInstructions![backupInstructionIndex],
                );
                this.initializeMarkdown();
            }
        }
    }

    findCriteriaIndex(criteria: GradingCriterion, exercise: Exercise) {
        return exercise.gradingCriteria!.findIndex((gradingCriteria) => {
            return gradingCriteria.id === criteria.id;
        });
    }

    findInstructionIndex(instruction: GradingInstruction, exercise: Exercise, criteriaIndex: number) {
        return exercise.gradingCriteria![criteriaIndex].structuredGradingInstructions?.findIndex((sgi) => {
            return sgi.id === instruction.id;
        });
    }

    /**
     * @function deleteInstruction
     * @desc Deletes selected instruction
     * @param instruction {GradingInstruction} the instruction which should be deleted
     * @param criteria {GradingCriterion} the criteria, which includes the instruction that will be deleted
     */
    deleteInstruction(instruction: GradingInstruction, criteria: GradingCriterion) {
        const criteriaIndex = this.exercise.gradingCriteria!.indexOf(criteria);
        const instructionIndex = this.exercise.gradingCriteria![criteriaIndex].structuredGradingInstructions.indexOf(instruction);
        this.exercise.gradingCriteria![criteriaIndex].structuredGradingInstructions.splice(instructionIndex, 1);
    }

    addInstruction(criteria: GradingCriterion) {
        this.addNewInstruction(criteria);
        this.initializeMarkdown();
    }

    /**
     * @function addNewInstruction
     * @desc Adds new grading instruction for desired grading criteria
     * @param criteria {GradingCriterion} the criteria, which includes the instruction that will be inserted
     */
    addNewInstruction(criteria: GradingCriterion) {
        const criteriaIndex = this.exercise.gradingCriteria!.indexOf(criteria);
        const instruction = new GradingInstruction();
        this.exercise.gradingCriteria![criteriaIndex].structuredGradingInstructions.push(instruction);
    }

    addGradingCriteria() {
        this.addNewGradingCriteria();
        this.initializeMarkdown();
    }

    /**
     * @function addNewGradingCriteria
     * @desc Adds new grading criteria for the exercise
     */
    addNewGradingCriteria() {
        const criteria = new GradingCriterion();
        criteria.structuredGradingInstructions = [];
        criteria.structuredGradingInstructions.push(new GradingInstruction());
        this.exercise.gradingCriteria!.push(criteria);
    }

    /**
     * @function onCriteriaTitleChange
     * @desc Detects changes for grading criteria title
     * @param {GradingCriterion} criteria the criteria, which includes title that will be changed
     */
    onCriteriaTitleChange($event: any, criteria: GradingCriterion) {
        const criteriaIndex = this.exercise.gradingCriteria!.indexOf(criteria);
        this.exercise.gradingCriteria![criteriaIndex].title = $event.target.value;
    }

    /**
     * @function resetCriteriaTitle
     * @desc Resets the whole grading criteria title
     * @param criteria {GradingCriterion} the criteria, which includes title that will be reset
     */
    resetCriteriaTitle(criteria: GradingCriterion) {
        const criteriaIndex = this.findCriteriaIndex(criteria, this.exercise);
        const backupCriteriaIndex = this.findCriteriaIndex(criteria, this.backupExercise);
        if (backupCriteriaIndex >= 0) {
            this.exercise.gradingCriteria![criteriaIndex].title = cloneDeep(this.backupExercise.gradingCriteria![backupCriteriaIndex].title);
        }
    }

    /**
     * @function deleteGradingCriteria
     * @desc Deletes the grading criteria with sub-grading instructions
     * @param criteria {GradingCriterion} the criteria, which will be deleted
     */
    deleteGradingCriteria(criteria: GradingCriterion) {
        const criteriaIndex = this.exercise.gradingCriteria!.indexOf(criteria);
        this.exercise.gradingCriteria!.splice(criteriaIndex, 1);
    }
}
