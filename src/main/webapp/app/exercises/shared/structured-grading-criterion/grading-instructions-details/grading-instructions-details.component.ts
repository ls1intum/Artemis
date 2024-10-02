import { AfterContentInit, ChangeDetectorRef, Component, Input, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { Exercise } from 'app/entities/exercise.model';
import { cloneDeep } from 'lodash-es';
import { faPlus, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { GradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-credits.action';
import { GradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-scale.action';
import { GradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-description.action';
import { GradingFeedbackAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-feedback.action';
import { GradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-usage-count.action';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent, TextWithDomainAction } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { GradingCriterionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-criterion.action';
import { GradingInstructionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-instruction.action';

@Component({
    selector: 'jhi-grading-instructions-details',
    templateUrl: './grading-instructions-details.component.html',
    styleUrls: ['./grading-instructions-details.component.scss'],
})
export class GradingInstructionsDetailsComponent implements OnInit, AfterContentInit {
    @ViewChildren('markdownEditors')
    private markdownEditors: QueryList<MarkdownEditorMonacoComponent>;
    @ViewChild('markdownEditor', { static: false })
    private markdownEditor: MarkdownEditorMonacoComponent;
    @Input()
    exercise: Exercise;
    private instructions: GradingInstruction[];
    private criteria: GradingCriterion[];

    backupExercise: Exercise;
    markdownEditorText = '';
    showEditMode: boolean;

    creditsAction = new GradingCreditsAction();
    gradingScaleAction = new GradingScaleAction();
    descriptionAction = new GradingDescriptionAction();
    feedbackAction = new GradingFeedbackAction();
    usageCountAction = new GradingUsageCountAction();
    gradingInstructionAction = new GradingInstructionAction(this.creditsAction, this.gradingScaleAction, this.descriptionAction, this.feedbackAction, this.usageCountAction);
    gradingCriterionAction = new GradingCriterionAction(this.gradingInstructionAction);

    domainActionsForMainEditor = [
        this.creditsAction,
        this.gradingScaleAction,
        this.descriptionAction,
        this.feedbackAction,
        this.usageCountAction,
        this.gradingInstructionAction,
        this.gradingCriterionAction,
    ];

    domainActionsForGradingInstructionParsing: TextEditorDomainAction[] = [
        this.creditsAction,
        this.gradingScaleAction,
        this.descriptionAction,
        this.feedbackAction,
        this.usageCountAction,
    ];

    // Icons
    faPlus = faPlus;
    faTrash = faTrash;
    faUndo = faUndo;

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

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
                this.markdownEditors.get(index)!.markdown = this.generateInstructionText(instruction);
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
                    // if it is a dummy criterion, leave out the action identifier
                    markdownText += this.generateInstructionsMarkdown(criterion);
                } else {
                    markdownText += `${GradingCriterionAction.IDENTIFIER} ${criterion.title}\n\t${this.generateInstructionsMarkdown(criterion)}`;
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
            GradingInstructionAction.IDENTIFIER +
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
        const creditsText = GradingCreditsAction.TEXT;
        const creditsIdentifier = GradingCreditsAction.IDENTIFIER;
        if (instruction.credits == undefined) {
            instruction.credits = parseFloat(creditsText) || 0;
        }
        return `${creditsIdentifier} ${instruction.credits || creditsText}`;
    }

    generateGradingScaleText(instruction: GradingInstruction): string {
        if (instruction.gradingScale == undefined) {
            instruction.gradingScale = GradingScaleAction.TEXT;
        }
        return `${GradingScaleAction.IDENTIFIER} ${instruction.gradingScale}`;
    }

    generateInstructionDescriptionText(instruction: GradingInstruction): string {
        if (instruction.instructionDescription == undefined) {
            instruction.instructionDescription = GradingDescriptionAction.TEXT;
        }
        return `${GradingDescriptionAction.IDENTIFIER} ${instruction.instructionDescription}`;
    }

    generateInstructionFeedback(instruction: GradingInstruction): string {
        if (instruction.feedback == undefined) {
            instruction.feedback = GradingFeedbackAction.TEXT;
        }
        return `${GradingFeedbackAction.IDENTIFIER} ${instruction.feedback}`;
    }

    generateUsageCount(instruction: GradingInstruction): string {
        if (instruction.usageCount == undefined) {
            instruction.usageCount = parseInt(GradingUsageCountAction.TEXT, 10) || 0;
        }
        return `${GradingUsageCountAction.IDENTIFIER} ${instruction.usageCount}`;
    }

    initializeExerciseGradingInstructionText(): string {
        return `${this.exercise.gradingInstructions || 'Add Assessment Instruction text here'}\n\n`;
    }

    prepareForSave(): void {
        this.cleanupExerciseGradingInstructions();
        this.markdownEditor.parseMarkdown();
        if (this.exercise.gradingInstructionFeedbackUsed) {
            this.markdownEditors.forEach((component) => {
                component.parseMarkdown(this.domainActionsForGradingInstructionParsing);
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

    hasCriterionAction(textWithDomainActions: TextWithDomainAction[]): boolean {
        return textWithDomainActions.some(({ action }) => action instanceof GradingCriterionAction);
    }

    /**
     * Creates criterion and instruction objects based on the parsed markdown text.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    createSubInstructionActions(textWithDomainActions: TextWithDomainAction[]): void {
        let instructionActions;
        let criterionActions;
        let endOfInstructionsAction = 0;
        if (!this.hasCriterionAction(textWithDomainActions)) {
            this.setParentForInstructionsWithNoCriterion(textWithDomainActions);
        } else {
            for (const { action } of textWithDomainActions) {
                endOfInstructionsAction++;
                this.setExerciseGradingInstructionText(textWithDomainActions);
                if (action instanceof GradingCriterionAction) {
                    instructionActions = textWithDomainActions.slice(0, endOfInstructionsAction - 1);
                    if (instructionActions.length !== 0) {
                        this.setParentForInstructionsWithNoCriterion(instructionActions);
                    }
                    criterionActions = textWithDomainActions.slice(endOfInstructionsAction - 1);
                    if (criterionActions.length !== 0) {
                        this.instructions = []; // resets the instructions array to be filled with the instructions of the criteria
                        this.groupInstructionsToCriteria(criterionActions);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Creates a dummy grading criterion object for each instruction that does not belong to a criterion and assigns the instruction to it.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    setParentForInstructionsWithNoCriterion(textWithDomainActions: TextWithDomainAction[]): void {
        for (const { action } of textWithDomainActions) {
            this.setExerciseGradingInstructionText(textWithDomainActions);
            if (action instanceof GradingInstructionAction) {
                const dummyCriterion = new GradingCriterion();
                const newInstruction = new GradingInstruction();
                dummyCriterion.structuredGradingInstructions = [];
                dummyCriterion.structuredGradingInstructions.push(newInstruction);
                this.instructions.push(newInstruction);
                this.criteria.push(dummyCriterion);
            }
        }
        this.exercise.gradingCriteria = this.criteria;
        this.setInstructionParameters(textWithDomainActions);
    }

    /**
     * Creates a grading criterion object for each criterion action found in the parsed markdown text and assigns the corresponding grading instructions to it.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    groupInstructionsToCriteria(textWithDomainActions: TextWithDomainAction[]): void {
        const initialCriterionActions = textWithDomainActions;
        if (this.exercise.gradingCriteria == undefined) {
            this.exercise.gradingCriteria = [];
        }
        for (const { text, action } of textWithDomainActions) {
            if (action instanceof GradingCriterionAction) {
                const newCriterion = new GradingCriterion();
                newCriterion.title = text;
                this.exercise.gradingCriteria.push(newCriterion);
                newCriterion.structuredGradingInstructions = [];
                const arrayWithoutCriterion = textWithDomainActions.slice(1); // remove the identifier after creating its criterion object
                let endOfCriterion = 0;
                for (const remainingTextWithDomainAction of arrayWithoutCriterion) {
                    const instrAction = remainingTextWithDomainAction.action;
                    endOfCriterion++;
                    if (instrAction instanceof GradingInstructionAction) {
                        const newInstruction = new GradingInstruction(); // create instruction objects that belong to the above created criterion
                        newCriterion.structuredGradingInstructions.push(newInstruction);
                        this.instructions.push(newInstruction);
                    }
                    if (instrAction instanceof GradingCriterionAction) {
                        textWithDomainActions = textWithDomainActions.slice(endOfCriterion, textWithDomainActions.length);
                        break;
                    }
                }
            }
        }
        this.setInstructionParameters(initialCriterionActions.filter(({ action }) => !(action instanceof GradingCriterionAction)));
    }

    /**
     * Sets the parameters of the GradingInstruction objects based on the parsed markdown text. Note that the instruction objects must be created before this method is called.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    setInstructionParameters(textWithDomainActions: TextWithDomainAction[]): void {
        let index = 0;
        for (const { text, action } of textWithDomainActions) {
            if (!this.instructions[index]) {
                break;
            }
            if (action instanceof GradingCreditsAction) {
                this.instructions[index].credits = parseFloat(text);
            } else if (action instanceof GradingScaleAction) {
                this.instructions[index].gradingScale = text;
            } else if (action instanceof GradingDescriptionAction) {
                this.instructions[index].instructionDescription = text;
            } else if (action instanceof GradingFeedbackAction) {
                this.instructions[index].feedback = text;
            } else if (action instanceof GradingUsageCountAction) {
                this.instructions[index].usageCount = parseInt(text, 10);
                index++; // index must be increased after the last parameter of the instruction to continue with the next instruction object
            }
        }
    }

    /**
     * Updates the grading instructions of the exercise based on the parsed markdown text.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    onDomainActionsFound(textWithDomainActions: TextWithDomainAction[]): void {
        this.instructions = [];
        this.criteria = [];
        this.exercise.gradingCriteria = [];
        this.createSubInstructionActions(textWithDomainActions);
    }

    onInstructionChange(textWithDomainActions: TextWithDomainAction[], instruction: GradingInstruction): void {
        this.instructions = [instruction];
        this.setInstructionParameters(textWithDomainActions);
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
     * Adds a new grading instruction for the specified grading criterion.
     * @param criterion The grading criterion that contains the instruction to insert.
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

    onCriterionTitleChange($event: any, criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        this.exercise.gradingCriteria![criterionIndex].title = $event.target.value;
    }

    resetCriterionTitle(criterion: GradingCriterion) {
        const criterionIndex = this.findCriterionIndex(criterion, this.exercise);
        const backupCriterionIndex = this.findCriterionIndex(criterion, this.backupExercise);
        if (backupCriterionIndex >= 0) {
            this.exercise.gradingCriteria![criterionIndex].title = cloneDeep(this.backupExercise.gradingCriteria![backupCriterionIndex].title);
        } else {
            criterion.title = '';
        }
    }

    deleteGradingCriterion(criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        this.exercise.gradingCriteria!.splice(criterionIndex, 1);
    }

    /**
     * Extracts the exercise grading instruction text from the start of the parsed markdown text.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    setExerciseGradingInstructionText(textWithDomainActions: TextWithDomainAction[]): void {
        if (!textWithDomainActions.length) {
            return;
        }
        const { text, action } = textWithDomainActions[0];
        if (action === undefined && text.length > 0) {
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

    updateGradingInstruction(instruction: GradingInstruction, criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        const instructionIndex = this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions.indexOf(instruction);
        this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions![instructionIndex] = instruction;
    }
}
