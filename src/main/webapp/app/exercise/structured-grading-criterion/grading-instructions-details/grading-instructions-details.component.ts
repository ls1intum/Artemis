import {
    AfterContentInit,
    Component,
    DestroyRef,
    DoCheck,
    Injector,
    OnInit,
    afterNextRender,
    computed,
    inject,
    input,
    output,
    signal,
    viewChild,
    viewChildren,
} from '@angular/core';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { isEqual } from 'lodash-es';
import { faPlus, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';
import { TextEditorDomainAction } from 'app/editor/monaco-editor/model/actions/text-editor-domain-action.model';
import { GradingCreditsAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-credits.action';
import { GradingScaleAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-scale.action';
import { GradingDescriptionAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-description.action';
import { GradingFeedbackAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-feedback.action';
import { GradingUsageCountAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-usage-count.action';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent, TextWithDomainAction } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { GradingCriterionAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-criterion.action';
import { GradingInstructionAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-instruction.action';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { AssessmentCriteriaGenerationService } from 'app/exercise/structured-grading-criterion/assessment-criteria-generation.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { defer, finalize } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { TumUiButtonComponent, TumUiConfirmDialogComponent, TumUiConfirmationService } from '@tumaet/ui-angular';
import { AccountService } from 'app/core/auth/account.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';

const GRADING_INSTRUCTION_PLACEHOLDER = 'Add Assessment Instruction text here';
const ASSESSMENT_CRITERIA_CONFIRMATION_KEY = 'assessment-criteria-generation-confirmation';

/** Template state derived from the mutable exercise entity. */
interface AssessmentCriteriaGenerationState {
    /** Whether the current user and exercise permit showing the generation action. */
    canShowButton: boolean;
    /** Translation key explaining which generation prerequisite is missing. */
    disabledReason?: string;
}

@Component({
    selector: 'jhi-grading-instructions-details',
    templateUrl: './grading-instructions-details.component.html',
    styleUrls: ['./grading-instructions-details.component.scss'],
    imports: [
        NgClass,
        TranslateDirective,
        FormsModule,
        FaIconComponent,
        HelpIconComponent,
        NgbTooltip,
        MarkdownEditorMonacoComponent,
        ArtemisTranslatePipe,
        TumUiButtonComponent,
        TumUiConfirmDialogComponent,
    ],
    providers: [TumUiConfirmationService],
})
export class GradingInstructionsDetailsComponent implements OnInit, AfterContentInit, DoCheck {
    private injector = inject(Injector);
    private readonly profileService = inject(ProfileService);
    private readonly generationService = inject(AssessmentCriteriaGenerationService);
    private readonly alertService = inject(AlertService);
    private readonly confirmationService = inject(TumUiConfirmationService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly accountService = inject(AccountService);

    private readonly markdownEditors = viewChildren<MarkdownEditorMonacoComponent>('markdownEditors');
    private readonly markdownEditor = viewChild.required<MarkdownEditorMonacoComponent>('markdownEditor');
    /** Exercise whose assessment instructions are displayed and edited. */
    readonly exercise = input.required<Exercise>();
    /** Whether the user may edit or generate assessment criteria. */
    readonly editable = input(true);
    /** Optional example solution supplied as generation context. */
    readonly exampleSolution = input<string>();
    /** Supplies exercise-type-specific context immediately before a generation request. */
    readonly additionalGenerationContext = input<() => string | undefined>(() => undefined);
    /** Synchronizes editor-owned state with the exercise immediately before generation. */
    readonly synchronizeExercise = input<() => void>(() => undefined);
    /** Emitted after generated criteria have replaced the current structured criteria. */
    readonly criteriaGenerated = output<void>();
    private instructions: GradingInstruction[] = [];
    private readonly criteria = signal<GradingCriterion[]>(undefined!);

    backupExercise!: Exercise; // set in ngOnInit() as a deep clone of the exercise() input before any edit-restore reads it
    readonly markdownEditorText = signal('');
    readonly showEditMode = signal<boolean>(undefined!);
    /** Whether an assessment-criteria request is currently in flight. */
    readonly isGenerating = signal(false);
    /** Whether the server profile enables Hyperion. */
    readonly hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);
    private readonly generationState = signal<AssessmentCriteriaGenerationState>(
        { canShowButton: false },
        { equal: (previous, current) => previous.canShowButton === current.canShowButton && previous.disabledReason === current.disabledReason },
    );
    /** Whether the generation action is applicable to the current exercise and user. */
    readonly canShowGenerationButton = computed(() => this.generationState().canShowButton);
    /** Translation key explaining why generation is unavailable, if applicable. */
    readonly generationDisabledReason = computed(() => this.generationState().disabledReason);
    /** Whether the generation action must currently be disabled. */
    readonly isGenerationDisabled = computed(() => this.generationDisabledReason() !== undefined || this.isGenerating());

    /** Refreshes signal-backed generation state when fields of the legacy mutable exercise entity change. */
    ngDoCheck(): void {
        const exercise = this.exercise();
        const course = exercise.course ?? exercise.exerciseGroup?.exam?.course;
        const canShowButton =
            this.hyperionEnabled &&
            this.editable() &&
            course?.id !== undefined &&
            !!(exercise.isAtLeastEditor || course.isAtLeastEditor || this.accountService.isAtLeastEditorForExercise(exercise));
        let disabledReason: string | undefined;
        if (!exercise.problemStatement?.trim()) {
            disabledReason = 'artemisApp.exercise.assessmentCriteriaGeneration.disabledProblemStatement';
        } else if (exercise.maxPoints === undefined || !Number.isFinite(exercise.maxPoints) || exercise.maxPoints <= 0) {
            disabledReason = 'artemisApp.exercise.assessmentCriteriaGeneration.disabledMaxPoints';
        } else if (exercise.bonusPoints !== undefined && (!Number.isFinite(exercise.bonusPoints) || exercise.bonusPoints < 0)) {
            disabledReason = 'artemisApp.exercise.assessmentCriteriaGeneration.disabledBonusPoints';
        }
        this.generationState.set({ canShowButton, disabledReason });
    }

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
    facArtemisIntelligence = facArtemisIntelligence;

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    ngOnInit() {
        this.criteria.set(this.exercise().gradingCriteria || []);
        this.backupExercise = deepClone(this.exercise());
        const markdown = this.exercise().gradingInstructionFeedbackUsed ? this.initializeExerciseGradingInstructionText() : this.generateMarkdown();
        this.markdownEditorText.set(markdown);
        this.showEditMode.set(true);
    }

    ngAfterContentInit() {
        if (this.exercise().gradingInstructionFeedbackUsed) {
            this.initializeMarkdown();
        }
    }

    initializeMarkdown() {
        // Defer until after the next render so the markdown editor view children (driven by the criteria @for) exist.
        afterNextRender(
            () => {
                let index = 0;
                this.criteria().forEach((criterion) => {
                    criterion.structuredGradingInstructions.forEach((instruction) => {
                        this.markdownEditors().at(index)!.setMarkdown(this.generateInstructionText(instruction));
                        index += 1;
                    });
                });
            },
            { injector: this.injector },
        );
    }

    generateMarkdown(): string {
        let markdownText = '';
        markdownText += this.initializeExerciseGradingInstructionText();
        const gradingCriteria = this.exercise().gradingCriteria;
        if (gradingCriteria) {
            for (const criterion of gradingCriteria) {
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
        return (
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
            '\n'
        );
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
        return `${this.exercise().gradingInstructions || GRADING_INSTRUCTION_PLACEHOLDER}\n\n`;
    }

    prepareForSave(): void {
        if (!this.editable()) {
            return;
        }
        this.cleanupExerciseGradingInstructions();
        this.markdownEditor().parseMarkdown();
        if (this.exercise().gradingInstructionFeedbackUsed) {
            this.markdownEditors().forEach((component) => {
                component.parseMarkdown(this.domainActionsForGradingInstructionParsing);
            });
        }
    }

    /**
     * @function cleanupExerciseGradingInstructions
     * @desc Clear the exercise grading instruction text to avoid double assignments
     */
    cleanupExerciseGradingInstructions() {
        if (!this.editable()) {
            return;
        }
        this.exercise().gradingInstructions = undefined;
    }

    hasCriterionAction(textWithDomainActions: TextWithDomainAction[]): boolean {
        return textWithDomainActions.some(({ action }) => action instanceof GradingCriterionAction);
    }

    /**
     * Creates criterion and instruction objects based on the parsed markdown text.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    createSubInstructionActions(textWithDomainActions: TextWithDomainAction[]): void {
        if (!this.editable()) {
            return;
        }
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
        if (!this.editable()) {
            return;
        }
        const criteria = [...this.criteria()];
        for (const { action } of textWithDomainActions) {
            this.setExerciseGradingInstructionText(textWithDomainActions);
            if (action instanceof GradingInstructionAction) {
                const dummyCriterion = new GradingCriterion();
                const newInstruction = new GradingInstruction();
                dummyCriterion.structuredGradingInstructions = [];
                dummyCriterion.structuredGradingInstructions.push(newInstruction);
                this.instructions.push(newInstruction);
                criteria.push(dummyCriterion);
            }
        }
        this.criteria.set(criteria);
        // Keep the exercise's gradingCriteria pointing at the same array the template iterates over.
        this.exercise().gradingCriteria = criteria;
        this.setInstructionParameters(textWithDomainActions);
    }

    /**
     * Creates a grading criterion object for each criterion action found in the parsed markdown text and assigns the corresponding grading instructions to it.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    groupInstructionsToCriteria(textWithDomainActions: TextWithDomainAction[]): void {
        if (!this.editable()) {
            return;
        }
        const initialCriterionActions = textWithDomainActions;
        const exercise = this.exercise();
        const gradingCriteria = exercise.gradingCriteria ?? (exercise.gradingCriteria = []);
        for (const { text, action } of textWithDomainActions) {
            if (action instanceof GradingCriterionAction) {
                const newCriterion = new GradingCriterion();
                newCriterion.title = text;
                gradingCriteria.push(newCriterion);
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
        if (!this.editable()) {
            return;
        }
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
        if (!this.editable()) {
            return;
        }
        this.instructions = [];
        this.criteria.set([]);
        this.exercise().gradingCriteria = [];
        this.createSubInstructionActions(textWithDomainActions);
    }

    onInstructionChange(textWithDomainActions: TextWithDomainAction[], instruction: GradingInstruction): void {
        if (!this.editable()) {
            return;
        }
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
        if (!this.editable()) {
            return;
        }
        const criterionIndex = this.findCriterionIndex(criterion, this.exercise());
        const gradingCriteria = this.exercise().gradingCriteria;
        if (gradingCriteria === undefined || criterionIndex < 0 || gradingCriteria[criterionIndex] === undefined) {
            return;
        }
        const backupCriterionIndex = this.findCriterionIndex(criterion, this.backupExercise);
        const instructions = gradingCriteria[criterionIndex].structuredGradingInstructions;
        const instructionIndex = instructions.indexOf(instruction);
        if (instructionIndex < 0) {
            return;
        }
        let backupInstructionIndex = undefined;

        if (backupCriterionIndex >= 0) {
            backupInstructionIndex = this.findInstructionIndex(instruction, this.backupExercise, backupCriterionIndex);

            if (backupInstructionIndex != undefined && backupInstructionIndex >= 0) {
                instructions[instructionIndex] = deepClone(this.backupExercise.gradingCriteria![backupCriterionIndex].structuredGradingInstructions[backupInstructionIndex]);
            }
        }
        if (backupCriterionIndex < 0 || backupInstructionIndex == undefined || backupInstructionIndex < 0) {
            instructions[instructionIndex] = new GradingInstruction();
        }
        this.initializeMarkdown();
    }

    findCriterionIndex(criterion: GradingCriterion, exercise: Exercise) {
        const gradingCriteria = exercise.gradingCriteria ?? [];
        const objectIndex = gradingCriteria.indexOf(criterion);
        if (objectIndex >= 0 || criterion.id === undefined) {
            return objectIndex;
        }
        return gradingCriteria.findIndex((gradingCriteria) => gradingCriteria.id === criterion.id);
    }

    findInstructionIndex(instruction: GradingInstruction, exercise: Exercise, criterionIndex: number) {
        const gradingCriteria = exercise.gradingCriteria;
        const criterion = gradingCriteria?.[criterionIndex];
        if (criterion === undefined) {
            return -1;
        }
        const instructions = criterion.structuredGradingInstructions ?? [];
        const objectIndex = instructions.indexOf(instruction);
        if (objectIndex >= 0 || instruction.id === undefined) {
            return objectIndex;
        }
        return instructions.findIndex((gradingInstruction) => gradingInstruction.id === instruction.id);
    }

    /**
     * @function deleteInstruction
     * @desc Deletes selected instruction
     * @param instruction {GradingInstruction} the instruction which should be deleted
     * @param criterion {GradingCriterion} the criteria, which includes the instruction that will be deleted
     */
    deleteInstruction(instruction: GradingInstruction, criterion: GradingCriterion) {
        if (!this.editable()) {
            return;
        }
        const criterionIndex = this.exercise().gradingCriteria!.indexOf(criterion);
        const instructionIndex = this.exercise().gradingCriteria![criterionIndex].structuredGradingInstructions.indexOf(instruction);
        this.exercise().gradingCriteria![criterionIndex].structuredGradingInstructions.splice(instructionIndex, 1);
    }

    addInstruction(criterion: GradingCriterion) {
        if (!this.editable()) {
            return;
        }
        this.addNewInstruction(criterion);
        this.initializeMarkdown();
    }

    /**
     * Adds a new grading instruction for the specified grading criterion.
     * @param criterion The grading criterion that contains the instruction to insert.
     */
    addNewInstruction(criterion: GradingCriterion) {
        if (!this.editable()) {
            return;
        }
        const criterionIndex = this.exercise().gradingCriteria!.indexOf(criterion);
        const instruction = new GradingInstruction();
        this.exercise().gradingCriteria![criterionIndex].structuredGradingInstructions.push(instruction);
    }

    addGradingCriterion() {
        if (!this.editable()) {
            return;
        }
        this.addNewGradingCriterion();
        this.initializeMarkdown();
    }

    addNewGradingCriterion() {
        if (!this.editable()) {
            return;
        }
        const criterion = new GradingCriterion();
        criterion.structuredGradingInstructions = [];
        criterion.structuredGradingInstructions.push(new GradingInstruction());
        if (this.exercise().gradingCriteria == undefined) {
            this.exercise().gradingCriteria = [criterion];
        } else {
            this.exercise().gradingCriteria!.push(criterion);
        }
    }

    onCriterionTitleChange($event: Event, criterion: GradingCriterion) {
        if (!this.editable()) {
            return;
        }
        const criterionIndex = this.exercise().gradingCriteria!.indexOf(criterion);
        this.exercise().gradingCriteria![criterionIndex].title = ($event.target as HTMLInputElement).value;
    }

    resetCriterionTitle(criterion: GradingCriterion) {
        if (!this.editable()) {
            return;
        }
        const criterionIndex = this.findCriterionIndex(criterion, this.exercise());
        const backupCriterionIndex = this.findCriterionIndex(criterion, this.backupExercise);
        if (backupCriterionIndex >= 0) {
            this.exercise().gradingCriteria![criterionIndex].title = deepClone(this.backupExercise.gradingCriteria![backupCriterionIndex].title);
        } else {
            criterion.title = '';
        }
    }

    deleteGradingCriterion(criterion: GradingCriterion) {
        if (!this.editable()) {
            return;
        }
        const criterionIndex = this.exercise().gradingCriteria!.indexOf(criterion);
        this.exercise().gradingCriteria!.splice(criterionIndex, 1);
    }

    /**
     * Extracts the exercise grading instruction text from the start of the parsed markdown text.
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    setExerciseGradingInstructionText(textWithDomainActions: TextWithDomainAction[]): void {
        if (!this.editable()) {
            return;
        }
        if (!textWithDomainActions.length) {
            return;
        }
        const { text, action } = textWithDomainActions[0];
        if (action === undefined && text.length > 0 && text.trim() !== GRADING_INSTRUCTION_PLACEHOLDER) {
            this.exercise().gradingInstructions = text;
        }
    }

    /**
     * Switches edit mode
     * Updates markdown text between mode switches
     */
    switchMode() {
        if (!this.editable()) {
            return;
        }
        this.showEditMode.update((mode) => !mode);
        this.markdownEditorText.set(this.generateMarkdown());
    }

    /** Validates current editor state and asks for confirmation before replacing existing criteria. */
    generateAssessmentCriteria(): void {
        if (!this.editable()) {
            return;
        }
        if (this.isGenerationDisabled()) {
            return;
        }

        if (!this.showEditMode() || this.exercise().gradingInstructionFeedbackUsed) {
            this.prepareForSave();
            if (!this.hasValidParsedCriteria()) {
                this.alertService.error('artemisApp.exercise.assessmentCriteriaGeneration.invalidSyntax');
                return;
            }
        }
        this.synchronizeExercise()();

        if (this.exercise().gradingCriteria?.length) {
            const usedCriteriaWarning = this.exercise().gradingInstructionFeedbackUsed
                ? ` ${this.translateService.instant('artemisApp.exercise.assessmentCriteriaGeneration.confirmationUsed')}`
                : '';
            this.confirmationService.confirm({
                key: ASSESSMENT_CRITERIA_CONFIRMATION_KEY,
                header: this.translateService.instant('artemisApp.exercise.assessmentCriteriaGeneration.confirmationHeader'),
                message: this.translateService.instant('artemisApp.exercise.assessmentCriteriaGeneration.confirmation') + usedCriteriaWarning,
                rejectLabel: this.translateService.instant('entity.action.cancel'),
                acceptLabel: this.translateService.instant('artemisApp.exercise.assessmentCriteriaGeneration.replaceAndGenerate'),
                acceptSeverity: 'danger',
                accept: () => this.requestAssessmentCriteria(),
            });
            return;
        }
        this.requestAssessmentCriteria();
    }

    /** Requests criteria and applies the response only if all generation inputs are unchanged. */
    private requestAssessmentCriteria(): void {
        if (!this.editable() || this.isGenerating()) {
            return;
        }
        const exercise = this.exercise();
        const exampleSolution = this.exampleSolution();
        const additionalContext = this.additionalGenerationContext()();
        const generationSnapshot = {
            problemStatement: exercise.problemStatement,
            maxPoints: exercise.maxPoints,
            bonusPoints: exercise.bonusPoints,
            includedInOverallScore: exercise.includedInOverallScore,
            gradingInstructions: exercise.gradingInstructions,
            gradingCriteria: deepClone(exercise.gradingCriteria),
            exampleSolution,
            additionalContext,
            gradingInstructionFeedbackUsed: exercise.gradingInstructionFeedbackUsed,
        };
        this.isGenerating.set(true);
        defer(() => this.generationService.generate(exercise, { exampleSolution, additionalContext }))
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.isGenerating.set(false)),
            )
            .subscribe({
                next: (criteria) => {
                    if (!this.editable()) {
                        return;
                    }
                    const currentExercise = this.exercise();
                    const currentSnapshot = {
                        problemStatement: currentExercise.problemStatement,
                        maxPoints: currentExercise.maxPoints,
                        bonusPoints: currentExercise.bonusPoints,
                        includedInOverallScore: currentExercise.includedInOverallScore,
                        gradingInstructions: currentExercise.gradingInstructions,
                        gradingCriteria: deepClone(currentExercise.gradingCriteria),
                        exampleSolution: this.exampleSolution(),
                        additionalContext: this.additionalGenerationContext()(),
                        gradingInstructionFeedbackUsed: currentExercise.gradingInstructionFeedbackUsed,
                    };
                    if (!isEqual(generationSnapshot, currentSnapshot)) {
                        return;
                    }
                    currentExercise.gradingCriteria = criteria;
                    this.criteria.set(criteria);
                    this.criteriaGenerated.emit();
                    if (this.exercise().gradingInstructionFeedbackUsed) {
                        this.markdownEditorText.set(this.initializeExerciseGradingInstructionText());
                        this.initializeMarkdown();
                    } else {
                        this.markdownEditorText.set(this.generateMarkdown());
                        if (!this.showEditMode()) {
                            this.markdownEditor().setMarkdown(this.markdownEditorText());
                        }
                    }
                    this.alertService.success('artemisApp.exercise.assessmentCriteriaGeneration.success');
                },
                error: (error) => onError(this.alertService, error),
            });
    }

    /** Returns whether all criteria parsed from markdown contain the required generation context. */
    private hasValidParsedCriteria(): boolean {
        return (this.exercise().gradingCriteria ?? []).every(
            (criterion) =>
                !!criterion.title?.trim() &&
                !!criterion.structuredGradingInstructions?.length &&
                criterion.structuredGradingInstructions.every(
                    (instruction) =>
                        Number.isFinite(instruction.credits) &&
                        !!instruction.gradingScale?.trim() &&
                        !!instruction.instructionDescription?.trim() &&
                        !!instruction.feedback?.trim() &&
                        Number.isInteger(instruction.usageCount) &&
                        instruction.usageCount! >= 0,
                ),
        );
    }

    updateGradingInstruction(instruction: GradingInstruction, criterion: GradingCriterion) {
        if (!this.editable()) {
            return;
        }
        const criterionIndex = this.exercise().gradingCriteria!.indexOf(criterion);
        const instructionIndex = this.exercise().gradingCriteria![criterionIndex].structuredGradingInstructions.indexOf(instruction);
        this.exercise().gradingCriteria![criterionIndex].structuredGradingInstructions[instructionIndex] = instruction;
    }
}
