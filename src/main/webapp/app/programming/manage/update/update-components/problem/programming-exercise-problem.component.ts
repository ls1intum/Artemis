import { Component, DestroyRef, Injector, OnDestroy, OnInit, inject, input, output, viewChild } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { faBan, faSave, faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
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
import { AlertService } from 'app/shared/service/alert.service';

import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { ProblemStatementAiOperationsHelper } from 'app/programming/manage/shared/problem-statement-ai-operations.helper';

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

    private translateService = inject(TranslateService);

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

    onCompetencyLinksChange(competencyLinks: ProgrammingExercise['competencyLinks']): void {
        const exercise = this.programmingExercise();
        if (exercise) {
            exercise.competencyLinks = competencyLinks;
            this.programmingExerciseChange.emit(exercise);
        }
    }

    onInstructionChange(problemStatement: string) {
        const exercise = this.programmingExercise();
        const previousContent = this.aiOps.currentProblemStatement();
        this.aiOps.currentProblemStatement.set(problemStatement);
        if (problemStatement !== previousContent) {
            this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
        }
        if (exercise) {
            exercise.problemStatement = problemStatement;
            this.programmingExerciseChange.emit(exercise);
        }
        this.problemStatementChange.emit(problemStatement);
    }
}
