import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, model, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { faRotate } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { Subject, catchError, debounceTime, filter, map, merge, of, switchMap, tap } from 'rxjs';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiMessageComponent, TumUiPanelComponent, TumUiSelectButtonComponent } from '@tumaet/ui-angular';

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

const MIN_TITLE_LENGTH = 3;
const MAX_TITLE_LENGTH = 255;

/** The server's rule for a programming exercise title, mirrored so a title Artemis will reject is caught before the draft is created. */
const TITLE_PATTERN = /^[\p{L}\p{M}\p{N}_\-\s]*$/u;

/** Long enough that a suggestion follows a pause in writing rather than a keystroke, short enough to have arrived by the time the brief is finished. */
const SUGGESTION_DEBOUNCE_MS = 800;

/** Mirrors {@code HyperionExerciseMetadataSuggestionService.DRAFT_MAX_POINTS}, for the window before the first suggestion arrives. */
const DRAFT_MAX_POINTS = 10;

/** The server's own key for "no build agent has a free sandbox slot", which is the one failure worth retrying as-is. */
const CAPACITY_ERROR_KEY = 'generationCapacityUnavailable';

/**
 * The server's keys for an identifier another exercise took first. Both are races rather than mistakes: the suggestion was free when it was made, and asking again produces one
 * that is free now.
 */
const IDENTIFIER_CONFLICT_ERROR_KEYS = ['shortnameAlreadyExists', 'titleAlreadyExists'];

/**
 * The brief that starts a whole-exercise generation run.
 *
 * The instructor states their intent once; Artemis derives everything that follows from it. What is asked for is what
 * cannot be derived: what to build, and in which build tool. The title and the difficulty are suggested from the brief
 * and stay editable. The short name, the package name and the points are derived and only shown, because inventing a
 * repository slug is a decision without a choice — the summary panel is there so a wrong one can still be caught.
 *
 * The draft is created unreleased and stays that way until the instructor releases it. Once a run has started, the
 * dialog is done: the run itself lives at its own URL.
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
    readonly title = signal('');
    readonly titleTouched = signal(false);
    /** Set as soon as the instructor changes the field themselves; from then on no suggestion may overwrite what they wrote. */
    readonly titleEdited = signal(false);
    /** The same rule for the difficulty: a value the instructor chose is theirs, and a later suggestion must not move it. */
    readonly difficultyEdited = signal(false);
    readonly suggesting = signal(false);
    /** Everything the last suggestion derived; undefined until one arrives, which is the only case the local identifier fallback covers. */
    readonly suggestion = signal<HyperionMetadataSuggestion | undefined>(undefined);
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
    /** So does what Artemis derived: it is there to be checked when something looks wrong, not to be read every time. */
    protected readonly summaryCollapsed = signal(true);

    protected readonly briefLength = computed(() => this.brief().trim().length);
    protected readonly briefTooShort = computed(() => this.briefLength() < MIN_BRIEF_LENGTH);
    protected readonly briefInvalid = computed(() => this.briefTooShort() || this.briefLength() > MAX_BRIEF_LENGTH);
    protected readonly showBriefError = computed(() => this.briefTouched() && this.briefTooShort());

    /** The field only appears once there is a brief worth naming, so an empty dialog does not open with an empty title to fill in. */
    protected readonly showTitleField = computed(() => !this.briefInvalid());
    protected readonly trimmedTitle = computed(() => this.title().trim());
    /**
     * An empty field is not an error: it is the state before the first suggestion arrives, and the fallback in {@link buildExercise} covers it. Only a title the instructor
     * typed that Artemis would refuse blocks generation.
     */
    protected readonly titleInvalid = computed(() => {
        const title = this.trimmedTitle();
        return title.length > 0 && (title.length < MIN_TITLE_LENGTH || !TITLE_PATTERN.test(title));
    });
    protected readonly showTitleError = computed(() => this.titleTouched() && this.titleInvalid());

    /** Says where a pre-selected difficulty came from, so a filled-in control does not read as a silent default nobody chose. */
    protected readonly difficultyFromBrief = computed(() => this.suggestion() !== undefined && !this.difficultyEdited());

    readonly canGenerate = computed(() => !this.briefInvalid() && !this.titleInvalid() && this.startError() === undefined);

    protected readonly startFailed = computed(() => this.startError() !== undefined);
    /** A capacity refusal changed nothing, so trying again is the answer; anything else may need the draft gone. */
    protected readonly capacityUnavailable = computed(() => this.errorKeyOf(this.startError()) === CAPACITY_ERROR_KEY);

    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly faRotate = faRotate;
    protected readonly minimumBriefLength = MIN_BRIEF_LENGTH;
    protected readonly maximumBriefLength = MAX_BRIEF_LENGTH;
    protected readonly minimumTitleLength = MIN_TITLE_LENGTH;
    protected readonly maximumTitleLength = MAX_TITLE_LENGTH;
    protected readonly buildToolOptions = [
        { value: ProjectType.PLAIN_MAVEN, labelKey: 'artemisApp.hyperion.generation.buildTools.maven' },
        { value: ProjectType.PLAIN_GRADLE, labelKey: 'artemisApp.hyperion.generation.buildTools.gradle' },
    ];
    protected readonly difficultyOptions = [
        { value: DifficultyLevel.EASY, labelKey: 'artemisApp.DifficultyLevel.EASY' },
        { value: DifficultyLevel.MEDIUM, labelKey: 'artemisApp.DifficultyLevel.MEDIUM' },
        { value: DifficultyLevel.HARD, labelKey: 'artemisApp.DifficultyLevel.HARD' },
    ];

    /** The brief once it is worth naming, and the empty string while it is not, so a shrinking brief cancels a pending suggestion instead of asking for one. */
    private readonly suggestionBrief = computed(() => (this.briefInvalid() ? '' : this.brief().trim()));

    private readonly regenerateRequests = new Subject<void>();

    constructor() {
        const whileTyping = toObservable(this.suggestionBrief).pipe(
            debounceTime(SUGGESTION_DEBOUNCE_MS),
            // Not merely dropped on arrival: once the instructor has titled the exercise themselves, there is nothing left to ask for.
            filter((brief) => brief.length > 0 && !this.titleEdited()),
        );
        const onRequest = this.regenerateRequests.pipe(
            map(() => this.suggestionBrief()),
            filter((brief) => brief.length > 0),
        );
        merge(whileTyping, onRequest)
            .pipe(
                tap(() => this.suggesting.set(true)),
                // switchMap, so a brief that keeps growing is answered by its latest version rather than by whichever request returns last.
                switchMap((brief) => this.requestSuggestion(brief)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((suggestion) => {
                this.suggesting.set(false);
                this.applySuggestion(suggestion);
            });
    }

    /** Records that the title is the instructor's now, so nothing in flight or asked for later overwrites it. */
    editTitle(title: string): void {
        this.title.set(title);
        this.titleEdited.set(true);
    }

    /** The same for the difficulty: choosing one settles it, and the hint saying where it came from goes away with it. */
    editDifficulty(difficulty: DifficultyLevel): void {
        this.difficulty.set(difficulty);
        this.difficultyEdited.set(true);
    }

    /**
     * Hands the title back to Hyperion: the instructor's edit is given up deliberately, which is what makes the button mean something. A difficulty they chose stays theirs —
     * this button is about the name.
     */
    regenerateTitle(): void {
        this.titleEdited.set(false);
        this.regenerateRequests.next();
    }

    /** Provisions the draft exercise and starts the run, then hands the instructor over to the run's own page. */
    generate(): void {
        if (!this.canGenerate() || this.provisioning()) {
            return;
        }
        this.setupFailed.set(false);
        this.startError.set(undefined);
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
        this.difficultyEdited.set(false);
        this.suggesting.set(false);
        this.suggestion.set(undefined);
        this.summaryCollapsed.set(true);
        this.difficulty.set(DifficultyLevel.MEDIUM);
        this.projectType.set(ProjectType.PLAIN_MAVEN);
        this.provisioning.set(false);
        this.setupFailed.set(false);
        this.startError.set(undefined);
        this.deleteFailed.set(false);
        this.deleting.set(false);
        this.createdExercise.set(undefined);
    }

    private requestSuggestion(brief: string) {
        return this.generationService.suggestMetadata(this.courseId(), brief, this.projectType()).pipe(catchError(() => of(undefined)));
    }

    /** Fills in what the instructor has not claimed for themselves, and always replaces the derived identifiers, which are nobody's to claim. */
    private applySuggestion(suggestion: HyperionMetadataSuggestion | undefined): void {
        this.suggestion.set(suggestion);
        if (!this.titleEdited()) {
            // The server answers with a usable title even when its model does not, so only a transport failure reaches the local fallback.
            this.title.set(suggestion?.title ?? this.translateService.instant('artemisApp.hyperion.generation.brief.draftTitle'));
        }
        if (suggestion && !this.difficultyEdited()) {
            this.difficulty.set(DifficultyLevel[suggestion.difficulty]);
        }
    }

    /**
     * Creates the draft and starts the run on it.
     *
     * @param mayReSuggest whether a lost race for the title or short name may be answered by asking for a fresh suggestion and trying once more. False on that second attempt,
     *                         so a course that keeps taking the name ends at the failure message rather than in a loop.
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
                    if (mayReSuggest && this.isIdentifierConflict(error)) {
                        this.reSuggestAndRetry();
                        return;
                    }
                    this.provisioning.set(false);
                    this.setupFailed.set(true);
                },
            });
    }

    /** Another exercise took the name between the suggestion and the submit. Ask once for one that is free now, and try again with it. */
    private reSuggestAndRetry(): void {
        this.requestSuggestion(this.brief().trim())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((suggestion) => {
                this.applySuggestion(suggestion);
                this.provisionAndStart(false);
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

    private isIdentifierConflict(error: unknown): boolean {
        const errorKey = this.errorKeyOf(error instanceof HttpErrorResponse ? error : undefined);
        return errorKey !== undefined && IDENTIFIER_CONFLICT_ERROR_KEYS.includes(errorKey);
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
        const suggestion = this.suggestion();
        // Reached only when no suggestion arrived at all — a transport failure — because the server answers with valid identifiers even when its model does not.
        const fallbackIdentifier = `gen${Date.now().toString(36)}`;
        // A successful run replaces this with the heading of the statement it wrote, so it is the draft title rather than the final one. The fallback covers the window before
        // the first suggestion arrives; the field is editable, so a clash with an existing exercise is the instructor's to resolve.
        exercise.title = this.trimmedTitle() || this.translateService.instant('artemisApp.hyperion.generation.brief.draftTitle');
        exercise.shortName = suggestion?.shortName ?? fallbackIdentifier;
        exercise.problemStatement = '';
        exercise.maxPoints = suggestion?.maxPoints ?? DRAFT_MAX_POINTS;
        exercise.difficulty = this.difficulty();
        // Keep the draft unreleased until an instructor deliberately schedules it after reviewing the result.
        exercise.releaseDate = dayjs().add(1, 'year');
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.projectType = this.projectType();
        exercise.packageName = suggestion?.packageName ?? `de.tum.cit.aet.${fallbackIdentifier}`;
        exercise.allowOnlineEditor = true;
        exercise.allowOfflineIde = true;
        return exercise;
    }
}
