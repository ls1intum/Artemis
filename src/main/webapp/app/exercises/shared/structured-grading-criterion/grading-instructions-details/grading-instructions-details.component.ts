import { AfterContentInit, ChangeDetectorRef, Component, Input, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { Exercise } from 'app/entities/exercise.model';
import { cloneDeep } from 'lodash-es';
import { faPlus, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { MonacoGradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-credits.action';
import { MonacoGradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-scale.action';
import { MonacoGradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-description.action';
import { MonacoGradingFeedbackAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-feedback.action';
import { MonacoGradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-usage-count.action';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoGradingCriterionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-criterion.action';
import { MonacoGradingInstructionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-instruction.action';

@Component({
    selector: 'jhi-grading-instructions-details',
    templateUrl: './grading-instructions-details.component.html',
    styleUrls: ['./grading-instructions-details.component.scss'],
})
export class GradingInstructionsDetailsComponent implements OnInit, AfterContentInit {
    /** Ace Editor configuration constants **/
    markdownEditorText = '';
    @ViewChildren('markdownEditors')
    private markdownEditors: QueryList<MarkdownEditorMonacoComponent>;
    @ViewChild('markdownEditor', { static: false })
    private markdownEditor: MarkdownEditorMonacoComponent;
    @Input()
    exercise: Exercise;
    private instructions: GradingInstruction[];
    private criteria: GradingCriterion[];

    backupExercise: Exercise;

    creditsAction = new MonacoGradingCreditsAction();
    gradingScaleAction = new MonacoGradingScaleAction();
    descriptionAction = new MonacoGradingDescriptionAction();
    feedbackAction = new MonacoGradingFeedbackAction();
    usageCountAction = new MonacoGradingUsageCountAction();
    gradingInstructionAction = new MonacoGradingInstructionAction(this.creditsAction, this.gradingScaleAction, this.descriptionAction, this.feedbackAction, this.usageCountAction);
    gradingCriterionAction = new MonacoGradingCriterionAction(this.gradingInstructionAction);

    domainActionsForMainEditor = [
        this.creditsAction,
        this.gradingScaleAction,
        this.descriptionAction,
        this.feedbackAction,
        this.usageCountAction,
        this.gradingInstructionAction,
        this.gradingCriterionAction,
    ];

    showEditMode: boolean;

    domainActionsForGradingInstructionParsing: MonacoEditorDomainAction[] = [
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
                    markdownText += `${MonacoGradingCriterionAction.IDENTIFIER} ${criterion.title}\n\t${this.generateInstructionsMarkdown(criterion)}`;
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
            MonacoGradingInstructionAction.IDENTIFIER +
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
        const creditsText = MonacoGradingCreditsAction.TEXT;
        const creditsIdentifier = MonacoGradingCreditsAction.IDENTIFIER;
        if (instruction.credits == undefined) {
            instruction.credits = parseFloat(creditsText) || 0;
        }
        return `${creditsIdentifier} ${instruction.credits || creditsText}`;
    }

    generateGradingScaleText(instruction: GradingInstruction): string {
        if (instruction.gradingScale == undefined) {
            instruction.gradingScale = MonacoGradingScaleAction.TEXT;
        }
        return `${MonacoGradingScaleAction.IDENTIFIER} ${instruction.gradingScale}`;
    }

    generateInstructionDescriptionText(instruction: GradingInstruction): string {
        if (instruction.instructionDescription == undefined) {
            instruction.instructionDescription = MonacoGradingDescriptionAction.TEXT;
        }
        return `${MonacoGradingDescriptionAction.IDENTIFIER} ${instruction.instructionDescription}`;
    }

    generateInstructionFeedback(instruction: GradingInstruction): string {
        if (instruction.feedback == undefined) {
            instruction.feedback = MonacoGradingFeedbackAction.TEXT;
        }
        return `${MonacoGradingFeedbackAction.IDENTIFIER} ${instruction.feedback}`;
    }

    generateUsageCount(instruction: GradingInstruction): string {
        if (instruction.usageCount == undefined) {
            instruction.usageCount = parseInt(MonacoGradingUsageCountAction.TEXT, 10) || 0;
        }
        return `${MonacoGradingUsageCountAction.IDENTIFIER} ${instruction.usageCount}`;
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

    hasCriterionAction(domainActions: [string, MonacoEditorDomainAction | undefined][]): boolean {
        return domainActions.some(([, action]) => action instanceof MonacoGradingCriterionAction);
    }

    /**
     * @function createSubInstructionActions
     * @desc 1. divides the input: domainActions into two subarrays:
     *          instructionActions, which consists of all stand-alone instructions
     *          criterionActions, which consists of instructions that belong to a criterion
     *       2. for each subarray, a method is called to create the criterion and instruction objects
     * @param domainActions containing tuples of [text, domainActionIdentifier]
     */
    createSubInstructionActions(domainActions: [string, MonacoEditorDomainAction | undefined][]): void {
        let instructionActions;
        let criterionActions;
        let endOfInstructionsAction = 0;
        if (!this.hasCriterionAction(domainActions)) {
            this.setParentForInstructionsWithNoCriterion(domainActions);
        } else {
            for (const [, action] of domainActions) {
                endOfInstructionsAction++;
                this.setExerciseGradingInstructionText(domainActions);
                if (action instanceof MonacoGradingCriterionAction) {
                    instructionActions = domainActions.slice(0, endOfInstructionsAction - 1);
                    if (instructionActions.length !== 0) {
                        this.setParentForInstructionsWithNoCriterion(instructionActions);
                    }
                    criterionActions = domainActions.slice(endOfInstructionsAction - 1);
                    if (criterionActions.length !== 0) {
                        this.instructions = []; // resets the instructions array to be filled with the instructions of the criteria
                        this.groupInstructionsToCriteria(criterionActions); // creates criterion object for each criterion and their corresponding instruction objects
                    }
                    break;
                }
            }
        }
    }

    /**
     * @function setParentForInstructionsWithNoCriterion
     * @desc 1. creates a dummy criterion object for each stand-alone instruction
     * @param domainActions containing tuples of [text, domain action identifier]
     */
    setParentForInstructionsWithNoCriterion(domainActions: [string, MonacoEditorDomainAction | undefined][]): void {
        for (const [, action] of domainActions) {
            this.setExerciseGradingInstructionText(domainActions);
            if (action instanceof MonacoGradingInstructionAction) {
                const dummyCriterion = new GradingCriterion();
                const newInstruction = new GradingInstruction();
                dummyCriterion.structuredGradingInstructions = [];
                dummyCriterion.structuredGradingInstructions.push(newInstruction);
                this.instructions.push(newInstruction);
                this.criteria.push(dummyCriterion);
            }
        }
        this.exercise.gradingCriteria = this.criteria;
        this.setInstructionParameters(domainActions);
    }

    /**
     * @function groupInstructionsToCriteria
     * @desc 1. creates a criterion for each MonacoGradingCriterionAction found in the domainActions array
     *          and creates the instruction objects of this criterion then assigns them to their parent criterion
     * @param domainActions containing tuples of [text, domain action identifier]
     */
    groupInstructionsToCriteria(domainActions: [string, MonacoEditorDomainAction | undefined][]): void {
        const initialCriterionActions = domainActions;
        if (this.exercise.gradingCriteria == undefined) {
            this.exercise.gradingCriteria = [];
        }
        for (const [text, action] of domainActions) {
            if (action instanceof MonacoGradingCriterionAction) {
                const newCriterion = new GradingCriterion();
                newCriterion.title = text;
                this.exercise.gradingCriteria.push(newCriterion);
                newCriterion.structuredGradingInstructions = [];
                const modifiedArray = domainActions.slice(1); // remove the identifier after creating its criterion object
                let endOfCriterion = 0;
                for (const [, instrAction] of modifiedArray) {
                    endOfCriterion++;
                    if (instrAction instanceof MonacoGradingInstructionAction) {
                        const newInstruction = new GradingInstruction(); // create instruction objects that belong to the above created criterion
                        newCriterion.structuredGradingInstructions.push(newInstruction);
                        this.instructions.push(newInstruction);
                    }
                    if (instrAction instanceof MonacoGradingCriterionAction) {
                        domainActions = domainActions.slice(endOfCriterion, domainActions.length);
                        break;
                    }
                }
            }
        }
        this.setInstructionParameters(initialCriterionActions.filter(([, action]) => !(action instanceof MonacoGradingCriterionAction)));
    }

    /**
     * @function setInstructionParameters
     * @desc 1. Gets a tuple of text and domain action identifiers (not including MonacoGradingCriterionAction) and assigns text values according to the domain action identifiers
     *       2. The tuple order is the same as the order of the actions in the markdown text inserted by the user
     *       instruction objects must be created before the method gets triggered
     * @param domainActions containing tuples of [text, domain action identifier]
     */
    setInstructionParameters(domainActions: [string, MonacoEditorDomainAction | undefined][]): void {
        let index = 0;
        for (const [text, action] of domainActions) {
            if (!this.instructions[index]) {
                break;
            }
            if (action instanceof MonacoGradingCreditsAction) {
                this.instructions[index].credits = parseFloat(text);
            } else if (action instanceof MonacoGradingScaleAction) {
                this.instructions[index].gradingScale = text;
            } else if (action instanceof MonacoGradingDescriptionAction) {
                this.instructions[index].instructionDescription = text;
            } else if (action instanceof MonacoGradingFeedbackAction) {
                this.instructions[index].feedback = text;
            } else if (action instanceof MonacoGradingUsageCountAction) {
                this.instructions[index].usageCount = parseInt(text, 10);
                index++; // index must be increased after the last parameter of the instruction to continue with the next instruction object
            }
        }
    }

    /**
     * @function onDomainActionsFound
     * @desc 1. Gets a tuple of text and domain action identifiers and assigns text values according to the domain action identifiers
     *       2. The tuple order is the same as the order of the actions in the markdown text inserted by the user
     * @param domainActions containing tuples of [text, domain action identifier]
     */
    onDomainActionsFound(domainActions: [string, MonacoEditorDomainAction | undefined][]): void {
        this.instructions = [];
        this.criteria = [];
        this.exercise.gradingCriteria = [];
        this.createSubInstructionActions(domainActions);
    }

    /**
     * @function onInstructionChange
     * @desc 1. Gets a tuple of text and domain action identifiers and assigns text values according to the domain action identifiers
     *       2. The tuple order is the same as the order of the actions in the markdown text inserted by the user
     * @param domainActions containing tuples of [text, domain action identifier]
     * @param {GradingInstruction} instruction
     */
    onInstructionChange(domainActions: [string, MonacoEditorDomainAction | undefined][], instruction: GradingInstruction): void {
        this.instructions = [instruction];
        this.setInstructionParameters(domainActions);
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
     * @desc Gets a tuple of text and domain action identifiers and assigns text values as grading instructions of exercise
     * @param domainActions The domain action identifiers along with their text values
     */
    setExerciseGradingInstructionText(domainActions: [string, MonacoEditorDomainAction | undefined][]): void {
        const [text, action] = domainActions[0];
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

    /**
     * Updates given grading instruction in exercise
     *
     * @param instruction needs to be updated
     * @param criterion includes instruction needs to be updated
     */
    updateGradingInstruction(instruction: GradingInstruction, criterion: GradingCriterion) {
        const criterionIndex = this.exercise.gradingCriteria!.indexOf(criterion);
        const instructionIndex = this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions.indexOf(instruction);
        this.exercise.gradingCriteria![criterionIndex].structuredGradingInstructions![instructionIndex] = instruction;
    }

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
}
