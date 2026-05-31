import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathExerciseService } from '../service/math-exercise.service';
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
import { MathBuilderComponent } from './math-builder/math-builder.component';
import { MathDerivationWorkspaceComponent } from './math-derivation-workspace/math-derivation-workspace.component';
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
    selector: 'jhi-math-exercise-update',
    templateUrl: './math-exercise-update.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        CategorySelectorComponent,
        DifficultyPickerComponent,
        IncludedInOverallScorePickerComponent,
        MarkdownEditorMonacoComponent,
        FormDateTimePickerComponent,
        ArtemisTranslatePipe,
        MathBuilderComponent,
        MathDerivationWorkspaceComponent,
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
export class MathExerciseUpdateComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private mathExerciseService = inject(MathExerciseService);
    private exerciseService = inject(ExerciseService);
    private router = inject(Router);
    private profileService = inject(ProfileService);

    mathExercise: MathExercise;
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
        this.activatedRoute.data.subscribe(({ mathExercise }) => {
            this.mathExercise = mathExercise;
            this.exerciseCategories.set(this.mathExercise.categories || []);
            if (!this.mathExercise.exampleDerivations) {
                this.mathExercise.exampleDerivations = [];
            }
        });
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.mathExercise.categories = categories;
    }

    validateDate() {
        this.exerciseService.validateDate(this.mathExercise);
    }

    onSourceExpressionChange(node: MathNode | undefined) {
        this.mathExercise.sourceExpression = node;
        this.mathExercise.exampleDerivations = [];
    }

    onTargetExpressionChange(node: MathNode | undefined) {
        this.mathExercise.targetExpression = node;
        // Workspaces re-evaluate isComplete automatically via the targetExpression signal input.
    }

    onGoalExpressionChange(node: MathNode | undefined) {
        this.mathExercise.goalExpression = node;
        this.mathExercise.exampleDerivations = [];
    }

    onGoalModeChange(mode: GoalMode) {
        this.mathExercise.goalMode = mode;
        // Reset example derivations — they're tied to the previous start expression.
        this.mathExercise.exampleDerivations = [];
        this.reachability.set(undefined);
    }

    checkReachability(): void {
        if (!this.mathExercise.id) {
            this.reachabilityError.set('artemisApp.mathExercise.reachability.saveFirst');
            return;
        }
        this.reachabilityError.set(undefined);
        this.reachabilityChecking.set(true);
        this.mathExerciseService.verifyReachability(this.mathExercise.id).subscribe({
            next: (report) => {
                this.reachability.set(report);
                if (!report) {
                    this.reachabilityError.set('artemisApp.mathExercise.reachability.notSupported');
                }
                this.reachabilityChecking.set(false);
            },
            error: () => {
                this.reachability.set(undefined);
                this.reachabilityError.set('artemisApp.mathExercise.reachability.failed');
                this.reachabilityChecking.set(false);
            },
        });
    }

    addExampleDerivation(): void {
        this.mathExercise.exampleDerivations = [...(this.mathExercise.exampleDerivations ?? []), []];
    }

    removeExampleDerivation(index: number): void {
        const updated = [...(this.mathExercise.exampleDerivations ?? [])];
        updated.splice(index, 1);
        this.mathExercise.exampleDerivations = updated;
    }

    onExampleStepsChange(index: number, steps: DerivationStep[]): void {
        const updated = [...(this.mathExercise.exampleDerivations ?? [])];
        updated[index] = steps;
        this.mathExercise.exampleDerivations = updated;
    }

    // Dev-only JSON import/export tools — gated by the isDev getter (profileService.isDevelopment()).
    get isDev(): boolean {
        return this.profileService.isDevelopment();
    }

    isDragOver = signal(false);

    exportJson(): void {
        const json = JSON.stringify(this.mathExercise, null, 2);
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `math-exercise-${this.mathExercise.id ?? 'new'}.json`;
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
                const parsed = JSON.parse(reader.result as string) as MathExercise;
                Object.assign(this.mathExercise, parsed);
            } catch {
                // malformed JSON — silently ignore in dev helper
            }
        };
        reader.readAsText(file);
    }

    save() {
        this.isSaving = true;
        if (this.mathExercise.id !== undefined) {
            this.mathExerciseService.update(this.mathExercise).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        } else {
            this.mathExerciseService.create(this.mathExercise).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
    }

    previousState() {
        this.router.navigate(['course-management', this.mathExercise.course?.id || this.activatedRoute.snapshot.params['courseId'], 'math-exercises']);
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
