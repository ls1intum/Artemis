import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, model, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import {
    TumUiButtonComponent,
    TumUiDialogComponent,
    TumUiInputDirective,
    TumUiInputNumberComponent,
    TumUiMessageComponent,
    TumUiPanelComponent,
    TumUiSelectButtonComponent,
    TumUiTagComponent,
} from '@tumaet/ui-angular';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import {
    MAX_PACKAGE_NAME_LENGTH,
    PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX,
    PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN,
    PROGRAMMING_EXERCISE_NAME_MAX_LENGTH,
    PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH,
    SHORT_NAME_PATTERN,
} from 'app/foundation/constants/input.constants';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionMetadataSuggestion } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

const MIN_BRIEF_LENGTH = 40;
const MAX_BRIEF_LENGTH = 8000;

/** {@code Exercise.validateTitle} on the server: shorter than three characters is rejected, and the column holds 255. */
const MIN_TITLE_LENGTH = 3;
const MAX_TITLE_LENGTH = PROGRAMMING_EXERCISE_NAME_MAX_LENGTH;

/** The server's rule for a programming exercise title ({@code Constants.TITLE_NAME_PATTERN}), mirrored so a title Artemis will reject is caught before the draft is created. */
const TITLE_PATTERN = /^[\p{L}\p{M}\p{N}_\-\s]*$/u;

/** The shortest name {@code Constants.SHORT_NAME_PATTERN} accepts: a letter plus two more alphanumerics. Stated separately only so the error message can name it. */
const MIN_SHORT_NAME_LENGTH = 3;

/**
 * What {@code Exercise.validateScoreSettings} demands of an exercise that counts towards the score — more than zero — expressed as the range
 * {@code ProgrammingExerciseUpdateComponent.validateExercisePoints} enforces in the ordinary exercise form, so both forms refuse the same numbers.
 */
const MIN_MAX_POINTS = 1;
const MAX_MAX_POINTS = 9999;

/** The server's own key for "no build agent has a free sandbox slot", which is the one failure worth retrying as-is. */
const CAPACITY_ERROR_KEY = 'generationCapacityUnavailable';

/** The server's keys for an identifier another exercise took first, mapped to the field the instructor would have to change. */
const IDENTIFIER_CONFLICT_FIELDS: Record<string, 'title' | 'shortName'> = { titleAlreadyExists: 'title', shortnameAlreadyExists: 'shortName' };

type IdentifierField = (typeof IDENTIFIER_CONFLICT_FIELDS)[string];

/**
 * The brief that starts a whole-exercise generation run.
 *
 * The instructor states their intent once, and then decides everything the draft is made of. Nothing is filled in behind their back: pressing "Suggest metadata" is the only thing
 * that writes into the title, the short name, the package name, the points and the difficulty, and every one of those is an editable field validated against the very rule the
 * create request will apply. Generation stays blocked until they are all present and valid, because a derived value quietly substituted at submit time is the behaviour this
 * dialog exists to avoid.
 *
 * The draft is created unreleased and stays that way until the instructor releases it. Once a run has started, the dialog is done: the run itself lives at its own URL.
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
        TumUiInputNumberComponent,
        TumUiMessageComponent,
        TumUiPanelComponent,
        TumUiSelectButtonComponent,
        TumUiTagComponent,
    ],
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

    readonly title = signal('');
    readonly titleTouched = signal(false);
    /** Set as soon as the instructor changes the field themselves; from then on only the suggest button may replace what they wrote. */
    readonly titleEdited = signal(false);

    readonly shortName = signal('');
    readonly shortNameTouched = signal(false);
    readonly shortNameEdited = signal(false);

    readonly packageName = signal('');
    readonly packageNameTouched = signal(false);
    readonly packageNameEdited = signal(false);

    readonly maxPoints = signal<number | undefined>(undefined);
    readonly maxPointsTouched = signal(false);
    readonly maxPointsEdited = signal(false);

    /** The one metadata value with a defensible default, because "medium" is a choice an instructor can leave standing rather than a name only Artemis could invent. */
    readonly difficulty = signal<DifficultyLevel>(DifficultyLevel.MEDIUM);
    readonly difficultyEdited = signal(false);

    readonly suggesting = signal(false);
    readonly suggestionFailed = signal(false);
    /** The last suggestion, which is what lets each field say whether its current value came from Hyperion or from the instructor. */
    readonly suggestion = signal<HyperionMetadataSuggestion | undefined>(undefined);

    /** The identifier another exercise took first, when it was the instructor's own and therefore not Artemis's to replace. */
    readonly conflictingIdentifier = signal<IdentifierField | undefined>(undefined);

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

    protected readonly trimmedTitle = computed(() => this.title().trim());
    protected readonly trimmedShortName = computed(() => this.shortName().trim());
    protected readonly trimmedPackageName = computed(() => this.packageName().trim());

    /** Blackbox exercises are validated against a single-identifier pattern; every other Java project against the dotted one. */
    private readonly packageNamePattern = computed(
        () => new RegExp(this.projectType() === ProjectType.MAVEN_BLACKBOX ? PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX : PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN),
    );

    /** Empty is incomplete rather than wrong, so an untouched field says "still missing" instead of shouting at an instructor who has not started. */
    protected readonly titleInvalid = computed(() => {
        const title = this.trimmedTitle();
        return title.length > 0 && (title.length < MIN_TITLE_LENGTH || title.length > MAX_TITLE_LENGTH || !TITLE_PATTERN.test(title));
    });
    protected readonly shortNameInvalid = computed(() => {
        const shortName = this.trimmedShortName();
        return shortName.length > 0 && (shortName.length > PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH || !SHORT_NAME_PATTERN.test(shortName));
    });
    protected readonly packageNameInvalid = computed(() => {
        const packageName = this.trimmedPackageName();
        return packageName.length > 0 && (packageName.length > MAX_PACKAGE_NAME_LENGTH || !this.packageNamePattern().test(packageName));
    });
    protected readonly maxPointsInvalid = computed(() => {
        const points = this.maxPoints();
        return points !== undefined && (!Number.isFinite(points) || points < MIN_MAX_POINTS || points > MAX_MAX_POINTS);
    });

    protected readonly showTitleError = computed(() => this.titleTouched() && this.titleInvalid());
    protected readonly showShortNameError = computed(() => this.shortNameTouched() && this.shortNameInvalid());
    protected readonly showPackageNameError = computed(() => this.packageNameTouched() && this.packageNameInvalid());
    protected readonly showMaxPointsError = computed(() => this.maxPointsTouched() && this.maxPointsInvalid());

    protected readonly titleTaken = computed(() => this.conflictingIdentifier() === 'title');
    protected readonly shortNameTaken = computed(() => this.conflictingIdentifier() === 'shortName');

    /** Says which values are Hyperion's, so a filled-in field never reads as a silent default nobody chose. */
    protected readonly titleFromSuggestion = computed(() => this.suggestion() !== undefined && !this.titleEdited());
    protected readonly shortNameFromSuggestion = computed(() => this.suggestion() !== undefined && !this.shortNameEdited());
    protected readonly packageNameFromSuggestion = computed(() => this.suggestion() !== undefined && !this.packageNameEdited());
    protected readonly maxPointsFromSuggestion = computed(() => this.suggestion() !== undefined && !this.maxPointsEdited());
    protected readonly difficultyFromSuggestion = computed(() => this.suggestion() !== undefined && !this.difficultyEdited());

    protected readonly hasSuggestion = computed(() => this.suggestion() !== undefined);
    readonly canSuggest = computed(() => !this.briefInvalid() && !this.suggesting());

    private readonly metadataIncomplete = computed(
        () => this.trimmedTitle().length === 0 || this.trimmedShortName().length === 0 || this.trimmedPackageName().length === 0 || this.maxPoints() === undefined,
    );
    private readonly metadataInvalid = computed(() => this.titleInvalid() || this.shortNameInvalid() || this.packageNameInvalid() || this.maxPointsInvalid());

    readonly canGenerate = computed(() => !this.briefInvalid() && !this.suggesting() && !this.metadataIncomplete() && !this.metadataInvalid() && this.startError() === undefined);

    /** What a disabled Generate button is waiting for, so it is never merely grey. */
    protected readonly generateBlockedReason = computed(() => {
        if (this.briefInvalid()) {
            return 'artemisApp.hyperion.generation.brief.blockedBrief';
        }
        if (this.suggesting()) {
            return 'artemisApp.hyperion.generation.brief.blockedSuggesting';
        }
        if (this.metadataIncomplete()) {
            return 'artemisApp.hyperion.generation.brief.blockedIncomplete';
        }
        if (this.metadataInvalid()) {
            return 'artemisApp.hyperion.generation.brief.blockedInvalid';
        }
        return undefined;
    });

    protected readonly startFailed = computed(() => this.startError() !== undefined);
    /** A capacity refusal changed nothing, so trying again is the answer; anything else may need the draft gone. */
    protected readonly capacityUnavailable = computed(() => this.errorKeyOf(this.startError()) === CAPACITY_ERROR_KEY);

    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly minimumBriefLength = MIN_BRIEF_LENGTH;
    protected readonly maximumBriefLength = MAX_BRIEF_LENGTH;
    protected readonly minimumTitleLength = MIN_TITLE_LENGTH;
    protected readonly maximumTitleLength = MAX_TITLE_LENGTH;
    protected readonly minimumShortNameLength = MIN_SHORT_NAME_LENGTH;
    protected readonly maximumShortNameLength = PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH;
    protected readonly maximumPackageNameLength = MAX_PACKAGE_NAME_LENGTH;
    protected readonly minimumMaxPoints = MIN_MAX_POINTS;
    protected readonly maximumMaxPoints = MAX_MAX_POINTS;
    protected readonly buildToolOptions = [
        { value: ProjectType.PLAIN_MAVEN, labelKey: 'artemisApp.hyperion.generation.buildTools.maven' },
        { value: ProjectType.PLAIN_GRADLE, labelKey: 'artemisApp.hyperion.generation.buildTools.gradle' },
    ];
    protected readonly difficultyOptions = [
        { value: DifficultyLevel.EASY, labelKey: 'artemisApp.DifficultyLevel.EASY' },
        { value: DifficultyLevel.MEDIUM, labelKey: 'artemisApp.DifficultyLevel.MEDIUM' },
        { value: DifficultyLevel.HARD, labelKey: 'artemisApp.DifficultyLevel.HARD' },
    ];

    /** The only path that writes metadata the instructor did not type, and it runs only when they press the button. */
    suggestMetadata(): void {
        if (!this.canSuggest()) {
            return;
        }
        this.suggestionFailed.set(false);
        this.suggesting.set(true);
        this.requestSuggestion().subscribe({
            next: (suggestion) => {
                this.suggesting.set(false);
                this.applySuggestion(suggestion, false);
            },
            error: () => {
                this.suggesting.set(false);
                this.suggestionFailed.set(true);
            },
        });
    }

    /** Records that the title is the instructor's now, so no recovery overwrites it behind their back. */
    editTitle(title: string): void {
        this.title.set(title);
        this.titleEdited.set(true);
        this.clearConflict('title');
    }

    editShortName(shortName: string): void {
        this.shortName.set(shortName);
        this.shortNameEdited.set(true);
        this.clearConflict('shortName');
    }

    editPackageName(packageName: string): void {
        this.packageName.set(packageName);
        this.packageNameEdited.set(true);
    }

    editMaxPoints(maxPoints: number | undefined): void {
        this.maxPoints.set(maxPoints);
        this.maxPointsEdited.set(true);
    }

    editDifficulty(difficulty: DifficultyLevel): void {
        this.difficulty.set(difficulty);
        this.difficultyEdited.set(true);
    }

    /** Provisions the draft exercise and starts the run, then hands the instructor over to the run's own page. */
    generate(): void {
        if (!this.canGenerate() || this.provisioning()) {
            return;
        }
        this.setupFailed.set(false);
        this.startError.set(undefined);
        this.conflictingIdentifier.set(undefined);
        this.provisioning.set(true);
        this.provisionAndStart(true);
    }

    /** Re-posts the run for the exercise that was already created, without provisioning a second one. */
    retryStart(): void {
        const exerciseId = this.createdExercise()?.id;
        if (exerciseId === undefined || this.provisioning()) {
            return;
        }
        this.startError.set(undefined);
        this.provisioning.set(true);
        this.startRun(exerciseId);
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
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.deleting.set(false);
                    this.reset();
                },
                error: () => {
                    this.deleting.set(false);
                    this.deleteFailed.set(true);
                },
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
        this.title.set('');
        this.titleTouched.set(false);
        this.titleEdited.set(false);
        this.shortName.set('');
        this.shortNameTouched.set(false);
        this.shortNameEdited.set(false);
        this.packageName.set('');
        this.packageNameTouched.set(false);
        this.packageNameEdited.set(false);
        this.maxPoints.set(undefined);
        this.maxPointsTouched.set(false);
        this.maxPointsEdited.set(false);
        this.difficulty.set(DifficultyLevel.MEDIUM);
        this.difficultyEdited.set(false);
        this.suggesting.set(false);
        this.suggestionFailed.set(false);
        this.suggestion.set(undefined);
        this.conflictingIdentifier.set(undefined);
        this.projectType.set(ProjectType.PLAIN_MAVEN);
        this.provisioning.set(false);
        this.setupFailed.set(false);
        this.startError.set(undefined);
        this.deleteFailed.set(false);
        this.deleting.set(false);
        this.createdExercise.set(undefined);
    }

    private clearConflict(field: IdentifierField): void {
        if (this.conflictingIdentifier() === field) {
            this.conflictingIdentifier.set(undefined);
        }
    }

    private requestSuggestion() {
        return this.generationService.suggestMetadata(this.courseId(), this.brief().trim(), this.projectType()).pipe(takeUntilDestroyed(this.destroyRef));
    }

    /**
     * Writes a suggestion into the fields.
     *
     * @param keepInstructorEdits whether a field the instructor has already typed in keeps their value. False when they pressed the suggest button, which is a deliberate request
     *                                for Hyperion's answer to everything; true when Artemis is recovering from a lost race, where nobody asked for their work to be thrown away.
     */
    private applySuggestion(suggestion: HyperionMetadataSuggestion, keepInstructorEdits: boolean): void {
        this.suggestion.set(suggestion);
        this.conflictingIdentifier.set(undefined);
        if (!keepInstructorEdits || !this.titleEdited()) {
            this.title.set(suggestion.title);
            this.titleEdited.set(false);
        }
        if (!keepInstructorEdits || !this.shortNameEdited()) {
            this.shortName.set(suggestion.shortName);
            this.shortNameEdited.set(false);
        }
        if (!keepInstructorEdits || !this.packageNameEdited()) {
            this.packageName.set(suggestion.packageName);
            this.packageNameEdited.set(false);
        }
        if (!keepInstructorEdits || !this.maxPointsEdited()) {
            this.maxPoints.set(suggestion.maxPoints);
            this.maxPointsEdited.set(false);
        }
        if (!keepInstructorEdits || !this.difficultyEdited()) {
            this.difficulty.set(DifficultyLevel[suggestion.difficulty]);
            this.difficultyEdited.set(false);
        }
    }

    /**
     * Creates the draft and starts the run on it.
     *
     * @param mayReSuggest whether a lost race for a value Hyperion suggested may be answered by asking for a fresh one and trying once more. False on that second attempt, so a
     *                         course that keeps taking the name ends at the conflict message rather than in a loop.
     */
    private provisionAndStart(mayReSuggest: boolean): void {
        this.programmingExerciseService
            .automaticSetup(this.buildExercise(this.courseId()), true)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    const created = response.body;
                    if (!created?.id) {
                        this.provisioning.set(false);
                        this.setupFailed.set(true);
                        return;
                    }
                    this.createdExercise.set(created);
                    this.exerciseCreated.emit(created);
                    this.startRun(created.id);
                },
                error: (error: unknown) => {
                    const conflict = this.identifierConflictField(error);
                    if (conflict === undefined) {
                        this.provisioning.set(false);
                        this.setupFailed.set(true);
                        return;
                    }
                    // A value the instructor typed is theirs even when it turns out to be taken: Artemis says so and lets them pick, rather than silently substituting its own.
                    const instructorsOwn = conflict === 'title' ? this.titleEdited() : this.shortNameEdited();
                    if (mayReSuggest && !instructorsOwn) {
                        this.reSuggestAndRetry();
                        return;
                    }
                    this.provisioning.set(false);
                    this.conflictingIdentifier.set(conflict);
                },
            });
    }

    /** Another exercise took a name Hyperion suggested between the suggestion and the submit. Ask once for one that is free now, keeping whatever the instructor typed themselves. */
    private reSuggestAndRetry(): void {
        this.requestSuggestion().subscribe({
            next: (suggestion) => {
                this.applySuggestion(suggestion, true);
                this.provisionAndStart(false);
            },
            error: () => {
                this.provisioning.set(false);
                this.setupFailed.set(true);
            },
        });
    }

    private startRun(exerciseId: number): void {
        this.generationService
            .generate(exerciseId, { mode: 'GENERATE', prompt: this.brief().trim() })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: ({ jobId }) => {
                    this.provisioning.set(false);
                    this.openRun(jobId);
                },
                error: (error: unknown) => {
                    this.provisioning.set(false);
                    // The draft exists but nothing is generating: offer to retry or to clean it up, and say which.
                    this.startError.set(error instanceof HttpErrorResponse ? error : new HttpErrorResponse({}));
                },
            });
    }

    private identifierConflictField(error: unknown): IdentifierField | undefined {
        const errorKey = this.errorKeyOf(error instanceof HttpErrorResponse ? error : undefined);
        return errorKey === undefined ? undefined : IDENTIFIER_CONFLICT_FIELDS[errorKey];
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

    /** Every value here is one the instructor can see and change in the dialog; {@link canGenerate} is what guarantees none of them is missing. */
    private buildExercise(courseId: number): ProgrammingExercise {
        const course = new Course();
        course.id = courseId;
        const exercise = new ProgrammingExercise(course, undefined);
        exercise.title = this.trimmedTitle();
        exercise.shortName = this.trimmedShortName();
        exercise.problemStatement = '';
        exercise.maxPoints = this.maxPoints();
        exercise.difficulty = this.difficulty();
        // Keep the draft unreleased until an instructor deliberately schedules it after reviewing the result.
        exercise.releaseDate = dayjs().add(1, 'year');
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.projectType = this.projectType();
        exercise.packageName = this.trimmedPackageName();
        exercise.allowOnlineEditor = true;
        exercise.allowOfflineIde = true;
        return exercise;
    }
}
