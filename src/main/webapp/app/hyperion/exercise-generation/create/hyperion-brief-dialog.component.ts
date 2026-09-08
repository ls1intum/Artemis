import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, model, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiMessageComponent } from '@tumaet/ui-angular';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionMetadataSuggestion } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

const MIN_BRIEF_LENGTH = 40;
const MAX_BRIEF_LENGTH = 8000;

/** Creates one unreleased Java draft from a brief, deriving metadata before using the normal exercise setup API. */
@Component({
    selector: 'jhi-hyperion-brief-dialog',
    templateUrl: './hyperion-brief-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, DecimalPipe, FormsModule, TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiMessageComponent],
})
export class HyperionBriefDialogComponent {
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly generationService = inject(HyperionExerciseGenerationService);
    private readonly registry = inject(HyperionJobRegistryService);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);

    readonly visible = model(false);
    readonly courseId = input.required<number>();
    readonly backRequested = output<void>();
    readonly exerciseCreated = output<ProgrammingExercise>();

    readonly brief = signal('');
    readonly briefTouched = signal(false);
    readonly provisioning = signal(false);
    readonly setupFailed = signal(false);
    readonly deleting = signal(false);
    readonly deleteFailed = signal(false);
    readonly createdExercise = signal<ProgrammingExercise | undefined>(undefined);
    readonly startError = signal<HttpErrorResponse | undefined>(undefined);

    private runBrief = '';
    private runCourseId = 0;

    protected readonly minimumBriefLength = MIN_BRIEF_LENGTH;
    protected readonly maximumBriefLength = MAX_BRIEF_LENGTH;
    protected readonly typicalBandParams = { min: 10, max: 25 };
    protected readonly briefLength = computed(() => this.brief().trim().length);
    protected readonly briefInvalid = computed(() => this.briefLength() < MIN_BRIEF_LENGTH || this.briefLength() > MAX_BRIEF_LENGTH);
    protected readonly showBriefError = computed(() => this.briefTouched() && this.briefInvalid());
    protected readonly startFailed = computed(() => this.startError() !== undefined);
    protected readonly capacityUnavailable = computed(() => this.errorKeyOf(this.startError()) === 'generationCapacityUnavailable');
    readonly busy = computed(() => this.provisioning() || this.deleting());
    readonly canGenerate = computed(() => !this.briefInvalid() && !this.busy() && this.createdExercise() === undefined);

    /** Freeze the brief for metadata, creation, and retries so one run cannot combine different requests. */
    generate(): void {
        if (!this.canGenerate()) {
            return;
        }
        this.runBrief = this.brief().trim();
        this.runCourseId = this.courseId();
        this.setupFailed.set(false);
        this.provisioning.set(true);
        this.deriveAndCreate(true);
    }

    private deriveAndCreate(mayRetryNameConflict: boolean): void {
        this.generationService
            .suggestMetadata(this.runCourseId, this.runBrief, ProjectType.GRADLE_GRADLE)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (metadata) => this.createDraft(metadata, mayRetryNameConflict),
                error: () => this.failSetup(),
            });
    }

    private createDraft(metadata: HyperionMetadataSuggestion, mayRetryNameConflict: boolean): void {
        this.programmingExerciseService
            .automaticSetup(this.buildExercise(metadata), true)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: ({ body: created }) => {
                    if (created?.id === undefined) {
                        this.failSetup();
                        return;
                    }
                    this.createdExercise.set(created);
                    this.exerciseCreated.emit(created);
                    this.startRun(created.id);
                },
                error: (error: unknown) => {
                    const key = this.errorKeyOf(error);
                    if (mayRetryNameConflict && (key === 'titleAlreadyExists' || key === 'shortnameAlreadyExists')) {
                        // A concurrent creation can take a derived name. Re-derive once, never loop indefinitely.
                        this.deriveAndCreate(false);
                    } else {
                        this.failSetup();
                    }
                },
            });
    }

    private failSetup(): void {
        this.provisioning.set(false);
        this.setupFailed.set(true);
    }

    /** Retry on the existing draft; never provision another exercise after a failed start. */
    retryStart(): void {
        const exerciseId = this.createdExercise()?.id;
        if (exerciseId === undefined || this.busy()) {
            return;
        }
        this.startError.set(undefined);
        this.provisioning.set(true);
        this.startRun(exerciseId);
    }

    private startRun(exerciseId: number): void {
        this.generationService
            .generate(exerciseId, { mode: 'GENERATE', prompt: this.runBrief })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: ({ jobId }) => {
                    this.provisioning.set(false);
                    this.openRun(jobId);
                },
                error: (error: unknown) => {
                    const failure = error instanceof HttpErrorResponse ? error : new HttpErrorResponse({});
                    if (failure.status === 0 || failure.status === 409) {
                        // The server may have accepted a start whose response was lost. Reattach before offering another start.
                        this.generationService
                            .getStatus(exerciseId)
                            .pipe(takeUntilDestroyed(this.destroyRef))
                            .subscribe({
                                next: (status) => {
                                    this.provisioning.set(false);
                                    if (status?.ownedByCaller) {
                                        this.openRun(status.jobId);
                                    } else {
                                        this.startError.set(failure);
                                    }
                                },
                                error: () => {
                                    this.provisioning.set(false);
                                    this.startError.set(failure);
                                },
                            });
                    } else {
                        this.provisioning.set(false);
                        this.startError.set(failure);
                    }
                },
            });
    }

    /** Remove the unused draft after a failed start, preserving the brief for another attempt. */
    deleteCreatedExercise(): void {
        const exerciseId = this.createdExercise()?.id;
        if (exerciseId === undefined || this.busy()) {
            return;
        }
        this.deleting.set(true);
        this.deleteFailed.set(false);
        this.programmingExerciseService
            .delete(exerciseId, false, false)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.deleting.set(false);
                    this.createdExercise.set(undefined);
                    this.startError.set(undefined);
                },
                error: () => {
                    this.deleting.set(false);
                    this.deleteFailed.set(true);
                },
            });
    }

    close(): void {
        if (!this.busy()) {
            this.visible.set(false);
        }
    }

    back(): void {
        if (!this.busy()) {
            this.backRequested.emit();
        }
    }

    reset(): void {
        this.brief.set('');
        this.briefTouched.set(false);
        this.provisioning.set(false);
        this.setupFailed.set(false);
        this.startError.set(undefined);
        this.deleteFailed.set(false);
        this.deleting.set(false);
        this.createdExercise.set(undefined);
        this.runBrief = '';
    }

    private openRun(jobId: string): void {
        const exercise = this.createdExercise();
        if (exercise?.id === undefined) {
            return;
        }
        const courseId = this.runCourseId;
        const exerciseId = exercise.id;
        this.registry.track({ jobId, exerciseId, courseId, exerciseTitle: exercise.title ?? '', mode: 'GENERATE' });
        this.visible.set(false);
        this.reset();
        void this.router.navigate(['/course-management', courseId, 'programming-exercises', exerciseId, 'generation']);
    }

    private errorKeyOf(error: unknown): string | undefined {
        const body = error instanceof HttpErrorResponse ? (error.error as { errorKey?: string } | undefined) : undefined;
        return typeof body?.errorKey === 'string' ? body.errorKey : undefined;
    }

    private buildExercise(metadata: HyperionMetadataSuggestion): ProgrammingExercise {
        const course = new Course();
        course.id = this.runCourseId;
        const exercise = new ProgrammingExercise(course, undefined);
        exercise.title = metadata.title;
        exercise.shortName = metadata.shortName;
        exercise.packageName = metadata.packageName;
        exercise.maxPoints = metadata.maxPoints;
        exercise.difficulty = DifficultyLevel[metadata.difficulty];
        exercise.problemStatement = '';
        // A future release date is required for generation eligibility; instructors review and schedule the draft afterwards.
        exercise.releaseDate = dayjs().add(1, 'year');
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        // Gradle student and test projects match the qualified worker harness.
        exercise.projectType = ProjectType.GRADLE_GRADLE;
        exercise.allowOnlineEditor = true;
        exercise.allowOfflineIde = true;
        return exercise;
    }
}
