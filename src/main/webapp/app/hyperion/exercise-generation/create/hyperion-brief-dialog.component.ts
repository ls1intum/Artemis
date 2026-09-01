import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, model, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { finalize, switchMap } from 'rxjs';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiMessageComponent, TumUiPanelComponent, TumUiSelectButtonComponent } from '@tumaet/ui-angular';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

const MIN_BRIEF_LENGTH = 40;
const MAX_BRIEF_LENGTH = 8000;

/** The server's own key for "no build agent has a free sandbox slot", which is the one failure worth retrying as-is. */
const CAPACITY_ERROR_KEY = 'generationCapacityUnavailable';

/**
 * The brief that starts a whole-exercise generation run.
 *
 * Everything this dialog collects is what the agent cannot infer: what to build, in which build tool, at which
 * difficulty. Title, points and dates are deliberately absent — Hyperion writes the title, and the rest is a normal
 * exercise edit afterwards. Once a run has started, the dialog is done: the run itself lives at its own URL.
 */
@Component({
    selector: 'jhi-hyperion-brief-dialog',
    templateUrl: './hyperion-brief-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        ArtemisTranslatePipe,
        TranslateDirective,
        DecimalPipe,
        FormsModule,
        TumUiButtonComponent,
        TumUiDialogComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
        TumUiPanelComponent,
        TumUiSelectButtonComponent,
    ],
})
export class HyperionBriefDialogComponent {
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly generationService = inject(HyperionExerciseGenerationService);
    private readonly registry = inject(HyperionJobRegistryService);
    private readonly translateService = inject(TranslateService);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);

    readonly visible = model(false);
    readonly courseId = input.required<number>();

    readonly backRequested = output<void>();
    readonly exerciseCreated = output<ProgrammingExercise>();

    readonly brief = signal('');
    readonly briefTouched = signal(false);
    readonly difficulty = signal<DifficultyLevel>(DifficultyLevel.MEDIUM);
    readonly projectType = signal<ProjectType>(ProjectType.PLAIN_MAVEN);
    readonly provisioning = signal(false);
    readonly setupFailed = signal(false);
    readonly deleteFailed = signal(false);
    readonly deleting = signal(false);
    /** The exercise a failed start left behind, which is the thing the recovery actions act on. */
    readonly createdExercise = signal<ProgrammingExercise | undefined>(undefined);
    /** Kept rather than discarded: which failure it was decides which recovery Artemis puts first. */
    readonly startError = signal<HttpErrorResponse | undefined>(undefined);

    /** The example starts folded away: it is a reference, not something to read past on every visit. */
    protected readonly exampleCollapsed = signal(true);

    protected readonly briefLength = computed(() => this.brief().trim().length);
    protected readonly briefTooShort = computed(() => this.briefLength() < MIN_BRIEF_LENGTH);
    protected readonly briefInvalid = computed(() => this.briefTooShort() || this.briefLength() > MAX_BRIEF_LENGTH);
    protected readonly showBriefError = computed(() => this.briefTouched() && this.briefTooShort());

    readonly canGenerate = computed(() => !this.briefInvalid() && this.startError() === undefined);

    protected readonly startFailed = computed(() => this.startError() !== undefined);
    /** A capacity refusal changed nothing, so trying again is the answer; anything else may need the draft gone. */
    protected readonly capacityUnavailable = computed(() => this.errorKeyOf(this.startError()) === CAPACITY_ERROR_KEY);

    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly minimumBriefLength = MIN_BRIEF_LENGTH;
    protected readonly maximumBriefLength = MAX_BRIEF_LENGTH;
    protected readonly buildToolOptions = [
        { value: ProjectType.PLAIN_MAVEN, labelKey: 'artemisApp.hyperion.generation.buildTools.maven' },
        { value: ProjectType.PLAIN_GRADLE, labelKey: 'artemisApp.hyperion.generation.buildTools.gradle' },
    ];
    protected readonly difficultyOptions = [
        { value: DifficultyLevel.EASY, labelKey: 'artemisApp.DifficultyLevel.EASY' },
        { value: DifficultyLevel.MEDIUM, labelKey: 'artemisApp.DifficultyLevel.MEDIUM' },
        { value: DifficultyLevel.HARD, labelKey: 'artemisApp.DifficultyLevel.HARD' },
    ];

    /** Provisions the draft exercise and starts the run, then hands the instructor over to the run's own page. */
    generate(): void {
        if (!this.canGenerate() || this.provisioning()) {
            return;
        }
        const exercise = this.buildExercise(this.courseId());
        this.setupFailed.set(false);
        this.startError.set(undefined);
        this.provisioning.set(true);
        this.programmingExerciseService
            .automaticSetup(exercise, true)
            .pipe(
                switchMap((response) => {
                    const created = response.body;
                    if (!created?.id) {
                        throw new Error('Programming exercise setup did not return an exercise');
                    }
                    this.createdExercise.set(created);
                    this.exerciseCreated.emit(created);
                    return this.generationService.generate(created.id, { mode: 'GENERATE', prompt: this.brief().trim() });
                }),
                finalize(() => this.provisioning.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: ({ jobId }) => this.openRun(jobId),
                error: (error: unknown) => {
                    if (this.createdExercise()) {
                        // The draft exists but nothing is generating: offer to retry or to clean it up, and say which.
                        this.startError.set(error instanceof HttpErrorResponse ? error : new HttpErrorResponse({}));
                    } else {
                        this.setupFailed.set(true);
                    }
                },
            });
    }

    /** Re-posts the run for the exercise that was already created, without provisioning a second one. */
    retryStart(): void {
        const exerciseId = this.createdExercise()?.id;
        if (exerciseId === undefined || this.provisioning()) {
            return;
        }
        this.startError.set(undefined);
        this.provisioning.set(true);
        this.generationService
            .generate(exerciseId, { mode: 'GENERATE', prompt: this.brief().trim() })
            .pipe(
                finalize(() => this.provisioning.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: ({ jobId }) => this.openRun(jobId),
                error: (error: unknown) => this.startError.set(error instanceof HttpErrorResponse ? error : new HttpErrorResponse({})),
            });
    }

    /** Removes the draft the failed start left behind, so a course is not littered with empty exercises. */
    deleteCreatedExercise(): void {
        const exerciseId = this.createdExercise()?.id;
        if (exerciseId === undefined || this.deleting()) {
            return;
        }
        this.deleting.set(true);
        this.deleteFailed.set(false);
        this.programmingExerciseService
            .delete(exerciseId, false, false)
            .pipe(
                finalize(() => this.deleting.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: () => this.reset(),
                error: () => this.deleteFailed.set(true),
            });
    }

    /** Closing after a successful start only closes the dialog; the run keeps going on the server. */
    close(): void {
        this.visible.set(false);
    }

    back(): void {
        this.backRequested.emit();
    }

    reset(): void {
        this.brief.set('');
        this.briefTouched.set(false);
        this.difficulty.set(DifficultyLevel.MEDIUM);
        this.projectType.set(ProjectType.PLAIN_MAVEN);
        this.provisioning.set(false);
        this.setupFailed.set(false);
        this.startError.set(undefined);
        this.deleteFailed.set(false);
        this.deleting.set(false);
        this.createdExercise.set(undefined);
    }

    private openRun(jobId: string): void {
        const exercise = this.createdExercise();
        const courseId = this.courseId();
        if (exercise?.id === undefined) {
            return;
        }
        this.registry.track({ jobId, exerciseId: exercise.id, courseId, exerciseTitle: exercise.title ?? '', mode: 'GENERATE' });
        this.visible.set(false);
        const exerciseId = exercise.id;
        this.reset();
        void this.router.navigate(['/course-management', courseId, 'programming-exercises', exerciseId, 'generation']);
    }

    private errorKeyOf(error: HttpErrorResponse | undefined): string | undefined {
        const body = error?.error as { errorKey?: string } | undefined;
        return typeof body?.errorKey === 'string' ? body.errorKey : undefined;
    }

    private buildExercise(courseId: number): ProgrammingExercise {
        const course = new Course();
        course.id = courseId;
        const exercise = new ProgrammingExercise(course, undefined);
        const generatedIdentifier = `gen${Date.now().toString(36)}`;
        // Hyperion replaces the title on save, so this is only ever what the exercise list shows in the meantime.
        exercise.title = this.translateService.instant('artemisApp.hyperion.generation.brief.draftTitle');
        exercise.shortName = generatedIdentifier;
        exercise.problemStatement = '';
        exercise.maxPoints = 10;
        exercise.difficulty = this.difficulty();
        // Keep the draft unreleased until an instructor deliberately schedules it after reviewing the result.
        exercise.releaseDate = dayjs().add(1, 'year');
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.projectType = this.projectType();
        exercise.packageName = `de.artemis.${generatedIdentifier}`;
        exercise.allowOnlineEditor = true;
        exercise.allowOfflineIde = true;
        return exercise;
    }
}
