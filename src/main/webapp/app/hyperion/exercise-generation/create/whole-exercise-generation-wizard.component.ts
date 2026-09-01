import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { finalize, switchMap } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faArrowRight, faGears, faKeyboard, faRobot, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiMessageComponent } from '@tumaet/ui-angular';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityComponent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

const MIN_BRIEF_LENGTH = 40;

type WizardStep = 'configure' | 'generating';

@Component({
    selector: 'jhi-whole-exercise-generation-wizard',
    templateUrl: './whole-exercise-generation-wizard.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        ArtemisTranslatePipe,
        FaIconComponent,
        FormsModule,
        HyperionGenerationActivityComponent,
        TumUiButtonComponent,
        TumUiDialogComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
    ],
})
export class WholeExerciseGenerationWizardComponent {
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly generationService = inject(HyperionExerciseGenerationService);
    private readonly alertService = inject(AlertService);
    private readonly router = inject(Router);

    readonly visible = input(false);
    readonly courseId = input<number | undefined>();
    readonly visibleChange = output<boolean>();
    readonly backRequested = output<void>();

    readonly generationActivity = viewChild(HyperionGenerationActivityComponent);
    readonly step = signal<WizardStep>('configure');
    readonly title = signal('');
    readonly shortName = signal('');
    readonly brief = signal('');
    readonly maxPoints = signal(10);
    readonly difficulty = signal<DifficultyLevel>(DifficultyLevel.MEDIUM);
    readonly projectType = signal<ProjectType>(ProjectType.PLAIN_MAVEN);
    readonly releaseDate = signal(dayjs().add(1, 'day').format('YYYY-MM-DDTHH:mm'));
    readonly dueDate = signal(dayjs().add(15, 'day').format('YYYY-MM-DDTHH:mm'));
    readonly provisioning = signal(false);
    readonly createdExercise = signal<ProgrammingExercise | undefined>(undefined);
    private readonly pendingJobId = signal<string | undefined>(undefined);
    private attachedJobId?: string;

    readonly canGenerate = computed(() => {
        const release = dayjs(this.releaseDate());
        const due = dayjs(this.dueDate());
        return (
            this.courseId() !== undefined &&
            this.title().trim().length >= 3 &&
            /^[a-zA-Z][a-zA-Z0-9]{2,18}$/.test(this.shortName().trim()) &&
            this.brief().trim().length >= MIN_BRIEF_LENGTH &&
            this.maxPoints() > 0 &&
            release.isAfter(dayjs()) &&
            due.isAfter(release) &&
            [ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE].includes(this.projectType())
        );
    });

    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly ProjectType = ProjectType;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faArrowRight = faArrowRight;
    protected readonly faGears = faGears;
    protected readonly faKeyboard = faKeyboard;
    protected readonly faRobot = faRobot;
    protected readonly faSpinner = faSpinner;

    constructor() {
        effect(() => {
            if (this.visible() && !this.createdExercise()) {
                this.step.set('configure');
            }
        });
        effect(() => {
            const jobId = this.pendingJobId();
            const activity = this.generationActivity();
            if (jobId && activity && jobId !== this.attachedJobId) {
                activity.attachToJob(jobId, 'GENERATE');
                this.attachedJobId = jobId;
            }
        });
    }

    updateTitle(value: string): void {
        const previousSuggestion = this.toShortName(this.title());
        this.title.set(value);
        if (!this.shortName() || this.shortName() === previousSuggestion) {
            this.shortName.set(this.toShortName(value));
        }
    }

    generate(): void {
        const courseId = this.courseId();
        if (!this.canGenerate() || courseId === undefined || this.provisioning()) {
            return;
        }

        const exercise = this.buildExercise(courseId);
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
                    this.step.set('generating');
                    return this.generationService.generate(created.id, { mode: 'GENERATE', prompt: this.brief().trim() });
                }),
                finalize(() => this.provisioning.set(false)),
            )
            .subscribe({
                next: ({ jobId }) => this.pendingJobId.set(jobId),
                error: () => {
                    this.alertService.error('artemisApp.hyperion.wholeExerciseWizard.startFailed');
                    if (!this.createdExercise()) {
                        this.step.set('configure');
                    }
                },
            });
    }

    openEditor(): void {
        const exercise = this.createdExercise();
        const courseId = this.courseId();
        const participationId = exercise?.templateParticipation?.id;
        if (exercise?.id === undefined || courseId === undefined || participationId === undefined) {
            return;
        }
        this.visibleChange.emit(false);
        void this.router.navigate(['course-management', courseId, 'programming-exercises', exercise.id, 'code-editor', RepositoryType.TEMPLATE, participationId]);
    }

    close(): void {
        this.visibleChange.emit(false);
    }

    back(): void {
        if (this.createdExercise()) {
            return;
        }
        this.backRequested.emit();
    }

    reset(): void {
        // Closing the dialog must not detach a running (or failed-but-inspectable) job. A page reload creates a fresh
        // host; until then reopening the Generate card returns to the same activity, matching the reconnect contract.
        if (this.createdExercise()) {
            return;
        }
        this.step.set('configure');
        this.title.set('');
        this.shortName.set('');
        this.brief.set('');
        this.maxPoints.set(10);
        this.difficulty.set(DifficultyLevel.MEDIUM);
        this.projectType.set(ProjectType.PLAIN_MAVEN);
        this.releaseDate.set(dayjs().add(1, 'day').format('YYYY-MM-DDTHH:mm'));
        this.dueDate.set(dayjs().add(15, 'day').format('YYYY-MM-DDTHH:mm'));
        this.provisioning.set(false);
        this.createdExercise.set(undefined);
        this.pendingJobId.set(undefined);
        this.attachedJobId = undefined;
    }

    private buildExercise(courseId: number): ProgrammingExercise {
        const course = new Course();
        course.id = courseId;
        const exercise = new ProgrammingExercise(course, undefined);
        exercise.title = this.title().trim();
        exercise.shortName = this.shortName().trim();
        exercise.problemStatement = this.brief().trim();
        exercise.maxPoints = this.maxPoints();
        exercise.difficulty = this.difficulty();
        exercise.releaseDate = dayjs(this.releaseDate());
        exercise.dueDate = dayjs(this.dueDate());
        exercise.assessmentDueDate = dayjs(this.dueDate()).add(7, 'day');
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.projectType = this.projectType();
        exercise.packageName = `de.artemis.${exercise.shortName.toLowerCase()}`;
        exercise.allowOnlineEditor = true;
        exercise.allowOfflineIde = true;
        return exercise;
    }

    private toShortName(title: string): string {
        const compact = title.replace(/[^a-zA-Z0-9]/g, '').slice(0, 19);
        return /^[a-zA-Z]/.test(compact) ? compact : '';
    }
}
