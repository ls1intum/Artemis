import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { finalize, interval, map, startWith, switchMap } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faArrowRight, faCircle, faCircleCheck, faKeyboard, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiMessageComponent, TumUiSelectButtonComponent } from '@tumaet/ui-angular';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityComponent, HyperionGenerationCompletedEvent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

const MIN_BRIEF_LENGTH = 40;
const MAX_BRIEF_LENGTH = 8000;

const PHASE_GROUPS = [
    { key: 'prepare', phases: ['PREPARING'] },
    { key: 'design', phases: ['DESIGNING'] },
    { key: 'verify', phases: ['VERIFYING'] },
    { key: 'review', phases: ['REVIEWING', 'REPAIRING'] },
    { key: 'save', phases: ['SAVING'] },
] as const;

type WizardStep = 'configure' | 'generating';

@Component({
    selector: 'jhi-whole-exercise-generation-wizard',
    templateUrl: './whole-exercise-generation-wizard.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        ArtemisTranslatePipe,
        DecimalPipe,
        FaIconComponent,
        FormsModule,
        HyperionGenerationActivityComponent,
        TumUiButtonComponent,
        TumUiDialogComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
        TumUiSelectButtonComponent,
    ],
})
export class WholeExerciseGenerationWizardComponent {
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly generationService = inject(HyperionExerciseGenerationService);
    private readonly router = inject(Router);

    readonly visible = input(false);
    readonly courseId = input<number | undefined>();
    readonly visibleChange = output<boolean>();
    readonly backRequested = output<void>();
    readonly exerciseCreated = output<ProgrammingExercise>();

    readonly generationActivity = viewChild(HyperionGenerationActivityComponent);
    readonly step = signal<WizardStep>('configure');
    readonly brief = signal('');
    readonly briefTouched = signal(false);
    readonly difficulty = signal<DifficultyLevel>(DifficultyLevel.MEDIUM);
    readonly projectType = signal<ProjectType>(ProjectType.PLAIN_MAVEN);
    readonly provisioning = signal(false);
    readonly setupFailed = signal(false);
    readonly startFailed = signal(false);
    readonly createdExercise = signal<ProgrammingExercise | undefined>(undefined);
    private readonly pendingJobId = signal<string | undefined>(undefined);
    private attachedJobId?: string;

    readonly canGenerate = computed(() => {
        const length = this.brief().trim().length;
        return (
            this.courseId() !== undefined &&
            length >= MIN_BRIEF_LENGTH &&
            length <= MAX_BRIEF_LENGTH &&
            [ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE].includes(this.projectType())
        );
    });
    readonly suggestedTitle = computed(() => this.titleFromBrief(this.brief()));
    private readonly now = toSignal(
        interval(1000).pipe(
            startWith(0),
            map(() => Date.now()),
        ),
        { initialValue: Date.now() },
    );
    readonly elapsedTime = computed(() => {
        const startedAt = this.generationActivity()?.startedAt();
        if (!startedAt) {
            return '0:00';
        }
        const seconds = Math.max(0, Math.floor((this.now() - Date.parse(startedAt)) / 1000));
        return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
    });
    readonly phaseSteps = computed(() => {
        const current = this.generationActivity()?.currentPhase();
        const currentIndex = PHASE_GROUPS.findIndex((group) => group.phases.some((phase) => phase === current));
        const visited = new Set(
            this.generationActivity()
                ?.events()
                .map((event) => event.phase)
                .filter((phase) => phase !== undefined),
        );
        return PHASE_GROUPS.map((group, index) => ({
            key: group.key,
            phases: group.phases,
            state: index === currentIndex ? 'current' : group.phases.some((phase) => visited.has(phase)) ? 'complete' : 'pending',
        }));
    });
    readonly currentDetail = computed(() => this.generationActivity()?.currentProgress()?.message);

    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly ProjectType = ProjectType;
    protected readonly minimumBriefLength = MIN_BRIEF_LENGTH;
    protected readonly maximumBriefLength = MAX_BRIEF_LENGTH;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faArrowRight = faArrowRight;
    protected readonly faKeyboard = faKeyboard;
    protected readonly faSpinner = faSpinner;
    protected readonly faCircle = faCircle;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly buildToolOptions = [
        { value: ProjectType.PLAIN_MAVEN, labelKey: 'artemisApp.hyperion.wholeExerciseWizard.buildTools.maven' },
        { value: ProjectType.PLAIN_GRADLE, labelKey: 'artemisApp.hyperion.wholeExerciseWizard.buildTools.gradle' },
    ];
    protected readonly difficultyOptions = [
        { value: DifficultyLevel.EASY, labelKey: 'artemisApp.DifficultyLevel.EASY' },
        { value: DifficultyLevel.MEDIUM, labelKey: 'artemisApp.DifficultyLevel.MEDIUM' },
        { value: DifficultyLevel.HARD, labelKey: 'artemisApp.DifficultyLevel.HARD' },
    ];

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

    generate(): void {
        const courseId = this.courseId();
        if (!this.canGenerate() || courseId === undefined || this.provisioning()) {
            return;
        }

        const exercise = this.buildExercise(courseId);
        this.setupFailed.set(false);
        this.startFailed.set(false);
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
                    this.step.set('generating');
                    return this.generationService.generate(created.id, { mode: 'GENERATE', prompt: this.brief().trim() });
                }),
                finalize(() => this.provisioning.set(false)),
            )
            .subscribe({
                next: ({ jobId }) => this.pendingJobId.set(jobId),
                error: () => {
                    if (this.createdExercise()) {
                        this.startFailed.set(true);
                    } else {
                        this.setupFailed.set(true);
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

    cancelGeneration(): void {
        this.generationActivity()?.cancel();
    }

    onGenerationCompleted(event: HyperionGenerationCompletedEvent): void {
        const exercise = this.createdExercise();
        if (!event.liveExerciseChanged || exercise?.id === undefined) {
            return;
        }
        this.programmingExerciseService.find(exercise.id).subscribe(({ body }) => {
            if (body) {
                this.createdExercise.set(body);
                this.exerciseCreated.emit(body);
            }
        });
    }

    back(): void {
        if (this.createdExercise()) {
            return;
        }
        this.backRequested.emit();
    }

    reset(): void {
        if (this.createdExercise()) {
            return;
        }
        this.step.set('configure');
        this.brief.set('');
        this.briefTouched.set(false);
        this.difficulty.set(DifficultyLevel.MEDIUM);
        this.projectType.set(ProjectType.PLAIN_MAVEN);
        this.provisioning.set(false);
        this.setupFailed.set(false);
        this.startFailed.set(false);
        this.createdExercise.set(undefined);
        this.pendingJobId.set(undefined);
        this.attachedJobId = undefined;
    }

    private buildExercise(courseId: number): ProgrammingExercise {
        const course = new Course();
        course.id = courseId;
        const exercise = new ProgrammingExercise(course, undefined);
        const generatedIdentifier = `gen${Date.now().toString(36)}`;
        exercise.title = this.suggestedTitle();
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

    private titleFromBrief(brief: string): string {
        const topic = brief.match(/(?:^|\n)\s*Topic(?: of the week)?\s*:\s*([^\n.]+)/i)?.[1]?.trim();
        if (topic) {
            return `${topic.charAt(0).toUpperCase()}${topic.slice(1)} exercise`;
        }
        return 'New Java programming exercise';
    }
}
