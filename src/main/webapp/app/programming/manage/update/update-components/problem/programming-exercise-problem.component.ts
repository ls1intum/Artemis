import { Component, DestroyRef, Injector, OnDestroy, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faBan, faSave, faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { CompetencyExerciseLink, CompetencyLearningObjectLink } from 'app/atlas/shared/entities/competency.model';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbAlert } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { TooltipModule } from 'primeng/tooltip';
import { TextareaModule } from 'primeng/textarea';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { InlineRefinementEvent, MAX_USER_PROMPT_LENGTH } from 'app/programming/manage/shared/problem-statement.utils';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { TranslateService } from '@ngx-translate/core';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ChecklistPanelComponent } from './checklist-panel/checklist-panel.component';
import { AlertService } from 'app/shared/service/alert.service';
import { MAX_PROGRAMMING_EXERCISE_PROBLEM_STATEMENT_LENGTH } from 'app/shared/constants/input.constants';

import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { ProblemStatementAiOperationsHelper } from 'app/programming/manage/shared/problem-statement-ai-operations.helper';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';

@Component({
    selector: 'jhi-programming-exercise-problem',
    templateUrl: './programming-exercise-problem.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss', './programming-exercise-problem.component.scss'],
    imports: [
        TranslateDirective,
        NgbAlert,
        TooltipModule,
        TextareaModule,
        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseEditableInstructionComponent,
        CompetencySelectionComponent,
        FormsModule,
        ArtemisTranslatePipe,
        ButtonModule,
        FaIconComponent,
        HelpIconComponent,
        ChecklistPanelComponent,
        GitDiffLineStatComponent,
        MessageModule,
    ],
})
export class ProgrammingExerciseProblemComponent implements OnInit, OnDestroy {
    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly MAX_USER_PROMPT_LENGTH = MAX_USER_PROMPT_LENGTH;

    programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    programmingExercise = input<ProgrammingExercise>();
    problemStatementChange = output<string>();
    programmingExerciseChange = output<ProgrammingExercise>();

    /** Tracks the authoritative competency links state, updated whenever links change from any source. */
    readonly activeCompetencyLinks = signal<CompetencyExerciseLink[]>([]);

    private translateService = inject(TranslateService);

    // Child component reference for refreshing competency selection
    private competencySelectionComponent = viewChild(CompetencySelectionComponent);

    readonly editableInstructions = viewChild<ProgrammingExerciseEditableInstructionComponent>('editableInstructions');

    /** Shared helper that encapsulates all AI-powered problem statement operations. */
    readonly aiOps = new ProblemStatementAiOperationsHelper(
        inject(ProblemStatementService),
        inject(AlertService),
        inject(ArtemisIntelligenceService),
        inject(ProfileService),
        inject(DestroyRef),
        inject(Injector),
    );

    // Delegate signals for template binding compatibility
    readonly userPrompt = this.aiOps.userPrompt;
    readonly isGeneratingOrRefining = this.aiOps.isGeneratingOrRefining;
    protected readonly isPromptNearLimit = this.aiOps.isPromptNearLimit;
    readonly hyperionEnabled = this.aiOps.hyperionEnabled;
    readonly showDiff = this.aiOps.showDiff;
    readonly allowSplitView = this.aiOps.allowSplitView;
    readonly addedLineCount = this.aiOps.addedLineCount;
    readonly removedLineCount = this.aiOps.removedLineCount;
    protected readonly templateLoaded = this.aiOps.templateLoaded;
    protected readonly isAiApplying = this.aiOps.isAiApplying;
    readonly shouldShowGenerateButton = this.aiOps.shouldShowGenerateButton;
    readonly maxProblemStatementLength = MAX_PROGRAMMING_EXERCISE_PROBLEM_STATEMENT_LENGTH;

    // Icons
    facArtemisIntelligence = facArtemisIntelligence;
    faSpinner = faSpinner;
    faBan = faBan;
    faSave = faSave;
    faTableColumns = faTableColumns;

    constructor() {
        this.aiOps.setChangeHandler({
            onContentChanged: (content, exercise) => {
                this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
                this.problemStatementChange.emit(content);
                this.programmingExerciseChange.emit(exercise);
            },
        });
    }

    ngOnInit(): void {
        const exercise = this.programmingExercise();
        this.activeCompetencyLinks.set([...(exercise?.competencyLinks ?? [])]);
        this.aiOps.currentProblemStatement.set(exercise?.problemStatement ?? '');
        this.aiOps.loadTemplate(exercise);
    }

    ngOnDestroy(): void {
        this.aiOps.destroy();
    }

    cancelAiOperation(): void {
        this.aiOps.cancelAiOperation();
    }

    handleProblemStatementAction(): void {
        this.aiOps.handleProblemStatementAction(this.programmingExercise(), this.editableInstructions());
    }

    closeDiffView(): void {
        this.aiOps.closeDiffView(this.programmingExercise(), this.editableInstructions());
    }

    revertAllChanges(): void {
        this.aiOps.revertAllChanges(this.programmingExercise(), this.editableInstructions());
    }

    onInlineRefinement(event: InlineRefinementEvent): void {
        this.aiOps.onInlineRefinement(this.programmingExercise(), this.editableInstructions(), event);
    }

    onDiffLineChange(event: { ready: boolean; lineChange: LineChange }): void {
        this.aiOps.onDiffLineChange(event);
    }

    getTranslatedPlaceholder(): string {
        return this.translateService.instant('artemisApp.programmingExercise.problemStatement.examplePlaceholder');
    }

    /**
     * Handles changes to competency links from either the checklist panel or the competency selection.
     * Also refreshes the CompetencySelectionComponent to reflect changes (e.g., newly created/linked competencies).
     */
    onCompetencyLinksChange(competencyLinks: CompetencyExerciseLink[] | CompetencyLearningObjectLink[] | undefined): void {
        if (this.programmingExerciseCreationConfig().isExamMode) return;
        const exercise = this.programmingExercise();
        if (exercise) {
            // undefined means all competencies were unlinked — treat as empty array.
            const updatedLinks = (competencyLinks ?? []).map((link) =>
                link instanceof CompetencyExerciseLink ? link : new CompetencyExerciseLink(link.competency, exercise, link.weight),
            );
            exercise.competencyLinks = updatedLinks;
            // Update the reactive signal so the checklist panel badges update immediately.
            this.activeCompetencyLinks.set(updatedLinks);
            this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
            this.programmingExerciseChange.emit(exercise);
            this.refreshCompetencySelection(competencyLinks ?? []);
        }
    }

    /**
     * Refreshes the CompetencySelectionComponent so newly linked/created competencies are visible.
     */
    private refreshCompetencySelection(competencyLinks: CompetencyExerciseLink[] | CompetencyLearningObjectLink[]): void {
        const selection = this.competencySelectionComponent();
        if (!selection) return;

        selection.refreshWithLinks(competencyLinks);
    }

    onDifficultyChange(difficulty: string): void {
        const exercise = this.programmingExercise();
        if (exercise) {
            const level = DifficultyLevel[difficulty as keyof typeof DifficultyLevel];
            if (level === undefined) {
                return;
            }
            exercise.difficulty = level;
            this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
            this.programmingExerciseChange.emit(exercise);
        }
    }

    /**
     * Opens the diff view to show changes proposed by a checklist AI action.
     * Uses the same diff review flow as refineProblemStatement().
     */
    onChecklistActionDiffRequest(proposedContent: string): void {
        this.aiOps.applyChecklistActionDiff(proposedContent, this.editableInstructions());
    }

    onInstructionChange(problemStatement: string) {
        const exercise = this.programmingExercise();
        const previousContent = this.aiOps.currentProblemStatement();
        this.aiOps.currentProblemStatement.set(problemStatement);
        if (problemStatement !== previousContent) {
            this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
            if (exercise) {
                exercise.problemStatement = problemStatement;
                this.programmingExerciseChange.emit(exercise);
            }
            this.problemStatementChange.emit(problemStatement);
        }
    }

    problemStatementLength = computed(() => this.aiOps.currentProblemStatement()?.length ?? 0);

    isProblemStatementTooLong = computed(() => this.problemStatementLength() > this.maxProblemStatementLength);

    isProblemStatementTooLong = computed(() => this.problemStatementLength() > this.maxProblemStatementLength);
}
