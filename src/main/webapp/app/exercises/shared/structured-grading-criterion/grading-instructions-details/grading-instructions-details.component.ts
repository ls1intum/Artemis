import { AfterContentInit, ChangeDetectorRef, Component, Input, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
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
import { cloneDeep } from 'lodash-es';
import { faPlus, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';

export enum GradingInstructionTableColumn {
    CREDITS = 'CREDITS',
    SCALE = 'SCALE',
    DESCRIPTION = 'DESCRIPTION',
    FEEDBACK = 'FEEDBACK',
    LIMIT = 'LIMIT',
}

@Component({
    selector: 'jhi-grading-instructions-details',
    templateUrl: './grading-instructions-details.component.html',
    styleUrls: ['./grading-instructions-details.component.scss'],
})
export class GradingInstructionsDetailsComponent implements OnInit, AfterContentInit {
    /** Ace Editor configuration constants **/
    markdownEditorText = '';
    @ViewChildren('markdownEditors')
    private markdownEditors: QueryList<MarkdownEditorComponent>;
    @ViewChild('markdownEditor', { static: false })
    private markdownEditor: MarkdownEditorComponent;
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

    showEditMode: boolean;
    readonly gradingInstructionTableColumn = GradingInstructionTableColumn;

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

    // Icons
    faPlus = faPlus;
    faTrash = faTrash;
    faUndo = faUndo;

    constructor(private changeDetector: ChangeDetectorRef) {}

    ngOnInit() {
        this.criteria = this.exercise.gradingCriteria || [];
        this.backupExercise = cloneDeep(this.exercise);
        this.markdownEditorText = this.generateMarkdown();
        this.showEditMode = true;
    }

    ngAfterContentInit() {
        if (this.exercise.gradingInstructionFeedbackUsed) {
            this.markdownEditorText = this.initializeExerciseGradingInstructionText();
            this.initializeMarkdown();
        }
    }

    initializeMarkdown() {
        let index = 0;
        this.changeDetector.detectChanges();
        this.criteria!.forEach((criterion) => {
            criterion.structuredGradingInstructions.forEach((instruction) => {
                this.markdownEditors.get(index)!.markdownTextChange(this.generateInstructionText(instruction));
                index += 1;
            });
        });
    }

    generateMarkdown(): string {
        let markdownText = '';
        markdownText += this.initializeExerciseGradingInstructionText();
        if (this.exercise.gradingCriteria) {
            for (const criterion of this.exercise.gradingCriteria) {
                if (criterion.title == undefined) {
                    // if it is a dummy criterion, leave out the command identifier
                    markdownText += this.generateInstructionsMarkdown(criterion);
                } else {
                    markdownText += GradingCriterionCommand.identifier + criterion.title + '\n' + '\t' + this.generateInstructionsMarkdown(criterion);
                }
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
        if (criterion.structuredGradingInstructions == undefined || criterion.structuredGradingInstructions.length === 0) {
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
        if (instruction.credits == undefined) {
            instruction.credits = parseFloat(CreditsCommand.text);
            return CreditsCommand.identifier + ' ' + CreditsCommand.text;
        }
        return CreditsCommand.identifier + ' ' + instruction.credits;
    }

    generateGradingScaleText(instruction: GradingInstruction): string {
        if (instruction.gradingScale == undefined) {
            instruction.gradingScale = GradingScaleCommand.text;
            return GradingScaleCommand.identifier + ' ' + GradingScaleCommand.text;
        }
        return GradingScaleCommand.identifier + ' ' + instruction.gradingScale;
    }

    generateInstructionDescriptionText(instruction: GradingInstruction): string {
        if (instruction.instructionDescription == undefined) {
            instruction.instructionDescription = InstructionDescriptionCommand.text;
            return InstructionDescriptionCommand.identifier + ' ' + InstructionDescriptionCommand.text;
        }
        return InstructionDescriptionCommand.identifier + ' ' + instruction.instructionDescription;
    }

    generateInstructionFeedback(instruction: GradingInstruction): string {
        if (instruction.feedback == undefined) {
            instruction.feedback = FeedbackCommand.text;
            return FeedbackCommand.identifier + ' ' + FeedbackCommand.text;
        }
        return FeedbackCommand.identifier + ' ' + instruction.feedback;
    }

    generateUsageCount(instruction: GradingInstruction): string {
        if (instruction.usageCount == undefined) {
            instruction.usageCount = parseInt(UsageCountCommand.text, 10);
            return UsageCountCommand.identifier + ' ' + UsageCountCommand.text;
        }
        return UsageCountCommand.identifier + ' ' + instruction.usageCount;
    }

    initializeExerciseGradingInstructionText(): string {
        if (this.exercise.gradingInstructions) {
            return this.exercise.gradingInstructions + '\n\n';
        } else {
            return 'Add Assessment Instruction text here' + '\n\n';
        }
    }

    prepareForSave(): void {
        this.cleanupExerciseGradingInstructions();
        this.markdownEditor.parse();
        if (this.exercise.gradingInstructionFeedbackUsed) {
            this.markdownEditors.forEach((component) => {
                component.parse();
            });
        }
    }

    /**
     * @function cleanupExerciseGradingInstructions
     * @desc Clear the exercise grading instruction text to avoid double assignments
     */
    cleanupExerciseGradingInstructions() {
        this.exercise.gradingInstructions = undefined;
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
                this.setExerciseGradingInstructionText(domainCommands);
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
            this.setExerciseGradingInstructionText(domainCommands);
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
        if (this.exercise.gradingCriteria == undefined) {
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
     *       2. The tuple order is the same as the order of the commands in the markdown text inserted by the user
     *       instruction objects must be created before the method gets triggered
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */

    setInstructionParameters(domainCommands: [string, DomainCommand | null][]): void {
        let index = 0;
        for (const [text, command] of domainCommands) {
            if (!this.instructions[index]) {
                break;
            }
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
     *       2. The tuple order is the same as the order of the commands in the markdown text inserted by the user
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
     *       2. The tuple order is the same as the order of the commands in the markdown text inserted by the user
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     * @param {GradingInstruction} instruction
     * @param {GradingCriterion} criteria
     */
    onInstructionChange(domainCommands: [string, DomainCommand | null][], instruction: GradingInstruction): void {
        this.instructions = [instruction];
        this.setInstructionParameters(domainCommands);
    }

    /**
     * @function resetInstruction
     * @desc Resets the whole instruction
     * @param instruction {GradingInstruction} the instruction, which will be reset
     * @param criterion {GradingCriterion} the criteria, which includes the instruction that will be reset
     */
    resetInstruction(instruction: GradingInstruction, criterion: GradingCriterion) {
        const criterionIndex = this.findCriterionIndex(criterion, this.exercise);
        const backupCriterionIndex = this.findCriterionIndex(criterion, this.backupExercise);
        const instructionIndex = this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions.indexOf(instruction);
        let backupInstructionIndex = undefined;

        if (backupCriterionIndex >= 0) {
            backupInstructionIndex = this.findInstructionIndex(instruction, this.backupExercise, backupCriterionIndex);

            if (backupInstructionIndex != undefined && backupInstructionIndex >= 0) {
                this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions![instructionIndex] = cloneDeep(
                    this.backupExercise.gradingCriteria![backupCriterionIndex].structuredGradingInstructions![backupInstructionIndex],
                );
            }
        }
        if (backupCriterionIndex < 0 || backupInstructionIndex == undefined || backupInstructionIndex < 0) {
            this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions![instructionIndex] = new GradingInstruction();
        }
        this.initializeMarkdown();
    }

    findCriterionIndex(criterion: GradingCriterion, exercise: Exercise) {
        return exercise.gradingCriteria!.findIndex((gradingCriteria) => {
            return gradingCriteria.id === criterion.id;
        });
    }

    findInstructionIndex(instruction: GradingInstruction, exercise: Exercise, criterionIndex: number) {
        return exercise.gradingCriteria![criterionIndex].structuredGradingInstructions?.findIndex((gradingInstruction) => {
            return gradingInstruction.id === instruction.id;
        });
    }

    /**
     * @function deleteInstruction
     * @desc Deletes selected instruction
     * @param instruction {GradingInstruction} the instruction which should be deleted
     * @param criterion {GradingCriterion} the criteria, which includes the instruction that will be deleted
     */
    deleteInstruction(instruction: GradingInstruction, criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        const instructionIndex = this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions.indexOf(instruction);
        this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions.splice(instructionIndex, 1);
    }

    addInstruction(criterion: GradingCriterion) {
        this.addNewInstruction(criterion);
        this.initializeMarkdown();
    }

    /**
     * @function addNewInstruction
     * @desc Adds new grading instruction for desired grading criteria
     * @param criterion {GradingCriterion} the criteria, which includes the instruction that will be inserted
     */
    addNewInstruction(criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        const instruction = new GradingInstruction();
        this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions.push(instruction);
    }

    addGradingCriterion() {
        this.addNewGradingCriterion();
        this.initializeMarkdown();
    }

    /**
     * @function addNewGradingCriteria
     * @desc Adds new grading criteria for the exercise
     */
    addNewGradingCriterion() {
        const criterion = new GradingCriterion();
        criterion.structuredGradingInstructions = [];
        criterion.structuredGradingInstructions.push(new GradingInstruction());
        if (this.exercise.gradingCriteria == undefined) {
            this.exercise.gradingCriteria = [criterion];
        } else {
            this.exercise.gradingCriteria!.push(criterion);
        }
    }

    /**
     * @function onCriteriaTitleChange
     * @desc Detects changes for grading criteria title
     * @param {GradingCriterion} criterion the criteria, which includes title that will be changed
     */
    onCriterionTitleChange($event: any, criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        this.exercise.gradingCriteria![criterionIndex].title = $event.target.value;
    }

    /**
     * @function resetCriteriaTitle
     * @desc Resets the whole grading criteria title
     * @param criterion {GradingCriterion} the criteria, which includes title that will be reset
     */
    resetCriterionTitle(criterion: GradingCriterion) {
        const criterionIndex = this.findCriterionIndex(criterion, this.exercise);
        const backupCriterionIndex = this.findCriterionIndex(criterion, this.backupExercise);
        if (backupCriterionIndex >= 0) {
            this.exercise.gradingCriteria![criterionIndex].title = cloneDeep(this.backupExercise.gradingCriteria![backupCriterionIndex].title);
        } else {
            criterion.title = '';
        }
    }

    /**
     * @function deleteGradingCriteria
     * @desc Deletes the grading criteria with sub-grading instructions
     * @param criterion {GradingCriterion} the criteria, which will be deleted
     */
    deleteGradingCriterion(criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        this.exercise.gradingCriteria!.splice(criterionIndex, 1);
    }

    /**
     * @function setExerciseGradingInstructionText
     * @desc Gets a tuple of text and domainCommandIdentifiers and assigns text values as grading instructions of exercise
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    setExerciseGradingInstructionText(domainCommands: [string, DomainCommand | null][]): void {
        const [text, command] = domainCommands[0];
        if (command === null && text.length > 0) {
            this.exercise.gradingInstructions = text;
        }
    }

    /**
     * Switches edit mode
     * Updates markdown text between mode switches
     */
    switchMode() {
        this.showEditMode = !this.showEditMode;
        this.markdownEditorText = this.generateMarkdown();
    }

    /**
     * Updates given grading instruction in exercise
     *
     * @param gradingInstruction needs to be updated
     * @param criterion includes instruction needs to be updated
     */
    updateGradingInstruction(instruction: GradingInstruction, criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        const instructionIndex = this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions.indexOf(instruction);
        this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions![instructionIndex] = instruction;
    }

    /**
     * Updates changed properties of the GradingInstruction.
     *
     * @param gradingInstruction that needs to be updated
     * @param criterion that includes the instruction needs to be updated
     * @param column that is updated
     */
    updateGradingInstructionProperty($event: any, instruction: GradingInstruction, criterion: GradingCriterion, column: GradingInstructionTableColumn) {
        switch (column) {
            case GradingInstructionTableColumn.CREDITS:
                instruction.credits = $event.target.value;
                break;
            case GradingInstructionTableColumn.SCALE:
                instruction.gradingScale = $event.target.value;
                break;
            case GradingInstructionTableColumn.DESCRIPTION:
                instruction.instructionDescription = $event.target.value;
                break;
            case GradingInstructionTableColumn.FEEDBACK:
                instruction.feedback = $event.target.value;
                break;
            case GradingInstructionTableColumn.LIMIT:
                instruction.usageCount = $event.target.value;
                break;
        }
        this.updateGradingInstruction(instruction, criterion);
    }
}
