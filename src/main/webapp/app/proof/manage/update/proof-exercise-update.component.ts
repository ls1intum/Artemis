import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofExerciseService } from '../service/proof-exercise.service';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MathNode } from '../../shared/entities/math-node.model';
import { DerivationStep } from '../../shared/entities/derivation-step.model';
import { GRADER_TYPES_AVAILABLE, GRADER_TYPE_LABELS, GraderType } from '../../shared/entities/grader-type.model';
import { GOAL_MODE_LABELS, GoalMode } from '../../shared/entities/goal-mode.model';
import { ReachabilityReport } from '../../shared/entities/hint-suggestion.model';
import { ProofBuilderComponent } from './proof-builder/proof-builder.component';
import { ProofDerivationWorkspaceComponent } from './proof-derivation-workspace/proof-derivation-workspace.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';

@Component({
    selector: 'jhi-proof-exercise-update',
    templateUrl: './proof-exercise-update.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        CategorySelectorComponent,
        DifficultyPickerComponent,
        IncludedInOverallScorePickerComponent,
        MarkdownEditorMonacoComponent,
        FormDateTimePickerComponent,
        ArtemisTranslatePipe,
        ProofBuilderComponent,
        ProofDerivationWorkspaceComponent,
        ButtonModule,
        CardModule,
        CheckboxModule,
        InputTextModule,
        MessageModule,
        SelectModule,
        TagModule,
        TextareaModule,
        TooltipModule,
    ],
})
export class ProofExerciseUpdateComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private proofExerciseService = inject(ProofExerciseService);
    private exerciseService = inject(ExerciseService);
    private router = inject(Router);
    // FIXME: dev-only — remove or gate behind a proper feature flag before merging to main
    private profileService = inject(ProfileService);

    proofExercise: ProofExercise;
    isSaving: boolean;
    exerciseCategories = signal<ExerciseCategory[]>([]);
    existingCategories = signal<ExerciseCategory[]>([]);
    onlyShowApplicableRules = signal(false);

    readonly graderTypeOptions: { value: GraderType; label: string; disabled: boolean }[] = (Object.keys(GRADER_TYPE_LABELS) as GraderType[]).map((value) => ({
        value,
        label: GRADER_TYPE_LABELS[value],
        disabled: !GRADER_TYPES_AVAILABLE.includes(value),
    }));

    readonly goalModeOptions: { value: GoalMode; label: string }[] = (Object.keys(GOAL_MODE_LABELS) as GoalMode[]).map((value) => ({
        value,
        label: GOAL_MODE_LABELS[value],
    }));

    reachability = signal<ReachabilityReport | undefined>(undefined);
    reachabilityChecking = signal(false);
    reachabilityError = signal<string | undefined>(undefined);

    releaseDateField = viewChild<FormDateTimePickerComponent>('releaseDate');
    startDateField = viewChild<FormDateTimePickerComponent>('startDate');
    dueDateField = viewChild<FormDateTimePickerComponent>('dueDate');
    assessmentDateField = viewChild<FormDateTimePickerComponent>('assessmentDueDate');

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ proofExercise }) => {
            this.proofExercise = proofExercise;
            this.exerciseCategories.set(this.proofExercise.categories || []);
            if (!this.proofExercise.exampleDerivations) {
                this.proofExercise.exampleDerivations = [];
            }
        });
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.proofExercise.categories = categories;
    }

    validateDate() {
        this.exerciseService.validateDate(this.proofExercise);
    }

    onSourceExpressionChange(node: MathNode | undefined) {
        this.proofExercise.sourceExpression = node;
        this.proofExercise.exampleDerivations = [];
    }

    onTargetExpressionChange(node: MathNode | undefined) {
        this.proofExercise.targetExpression = node;
        // Workspaces re-evaluate isComplete automatically via the targetExpression signal input.
    }

    onGoalExpressionChange(node: MathNode | undefined) {
        this.proofExercise.goalExpression = node;
        this.proofExercise.exampleDerivations = [];
    }

    onGoalModeChange(mode: GoalMode) {
        this.proofExercise.goalMode = mode;
        // Reset example derivations — they're tied to the previous start expression.
        this.proofExercise.exampleDerivations = [];
        this.reachability.set(undefined);
    }

    checkReachability(): void {
        if (!this.proofExercise.id) {
            this.reachabilityError.set('Save the exercise first to run the reachability check.');
            return;
        }
        this.reachabilityError.set(undefined);
        this.reachabilityChecking.set(true);
        this.proofExerciseService.verifyReachability(this.proofExercise.id).subscribe({
            next: (report) => {
                this.reachability.set(report);
                if (!report) {
                    this.reachabilityError.set('Reachability check is not supported for this exercise.');
                }
                this.reachabilityChecking.set(false);
            },
            error: () => {
                this.reachability.set(undefined);
                this.reachabilityError.set('Reachability check failed.');
                this.reachabilityChecking.set(false);
            },
        });
    }

    addExampleDerivation(): void {
        this.proofExercise.exampleDerivations = [...(this.proofExercise.exampleDerivations ?? []), []];
    }

    removeExampleDerivation(index: number): void {
        const updated = [...(this.proofExercise.exampleDerivations ?? [])];
        updated.splice(index, 1);
        this.proofExercise.exampleDerivations = updated;
    }

    onExampleStepsChange(index: number, steps: DerivationStep[]): void {
        const updated = [...(this.proofExercise.exampleDerivations ?? [])];
        updated[index] = steps;
        this.proofExercise.exampleDerivations = updated;
    }

    // FIXME: dev-only helpers — remove or gate behind a proper feature flag before merging to main
    get isDev(): boolean {
        return this.profileService.isDevelopment();
    }

    isDragOver = signal(false);

    exportJson(): void {
        const json = JSON.stringify(this.proofExercise, null, 2);
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `proof-exercise-${this.proofExercise.id ?? 'new'}.json`;
        a.click();
        URL.revokeObjectURL(url);
    }

    importJson(event: Event): void {
        const file = (event.target as HTMLInputElement).files?.[0];
        if (!file) return;
        this.readJsonFile(file);
        (event.target as HTMLInputElement).value = '';
    }

    onFileDrop(event: DragEvent): void {
        event.preventDefault();
        this.isDragOver.set(false);
        const file = event.dataTransfer?.files?.[0];
        if (file) this.readJsonFile(file);
    }

    private readJsonFile(file: File): void {
        const reader = new FileReader();
        reader.onload = () => {
            try {
                const parsed = JSON.parse(reader.result as string) as ProofExercise;
                Object.assign(this.proofExercise, parsed);
            } catch {
                // malformed JSON — silently ignore in dev helper
            }
        };
        reader.readAsText(file);
    }

    save() {
        this.isSaving = true;
        if (this.proofExercise.id !== undefined) {
            this.proofExerciseService.update(this.proofExercise).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        } else {
            this.proofExerciseService.create(this.proofExercise).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
    }

    previousState() {
        this.router.navigate(['course-management', this.proofExercise.course?.id || this.activatedRoute.snapshot.params['courseId'], 'proof-exercises']);
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
