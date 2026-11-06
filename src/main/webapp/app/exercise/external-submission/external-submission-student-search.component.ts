import { Component, DestroyRef, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiAutoCompleteComponent, TumAetUiAutoCompleteOptionEvent, TumAetUiChipComponent, TumAetUiFormFieldComponent } from '@tumaet/ui-angular';
import { Subject, catchError, map, of, switchMap } from 'rxjs';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExternalSubmissionService } from 'app/exercise/external-submission/external-submission.service';
import { ExternalSubmissionCandidate, ExternalSubmissionIneligibility } from 'app/exercise/external-submission/external-submission-candidate.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * Minimum length of a search term. Mirrors {@code ExternalSubmissionEligibilityService.MIN_SEARCH_TERM_LENGTH} on the server; change both together.
 */
export const EXTERNAL_SUBMISSION_SEARCH_MIN_LENGTH = 3;

const SEARCH_PAGE_SIZE = 10;
const SEARCH_DEBOUNCE_MS = 300;

/** Error keys the server sends when the exercise cannot take external submissions at all; any other failure shows the generic message. */
const EXERCISE_LEVEL_ERROR_KEYS = ['error.externalSubmissionBeforeDueDate', 'error.externalSubmissionForQuizExercise'];

const INELIGIBILITY_KEYS: Record<ExternalSubmissionIneligibility, string> = {
    NOT_IN_COURSE: 'artemisApp.externalSubmission.ineligible.notInCourse',
    NO_STUDENT_EXAM: 'artemisApp.externalSubmission.ineligible.noStudentExam',
    EXERCISE_NOT_IN_STUDENT_EXAM: 'artemisApp.externalSubmission.ineligible.exerciseNotInStudentExam',
    RESULT_ALREADY_EXISTS: 'artemisApp.externalSubmission.ineligible.resultAlreadyExists',
};

interface CandidateOption {
    label: string;
    disabled: boolean;
    candidate: ExternalSubmissionCandidate;
}

type SearchOutcome = { searchTerm: string } & ({ candidates: ExternalSubmissionCandidate[]; totalMatches: number } | { errorKey: string });

interface Hint {
    key: string;
    params?: Record<string, number>;
}

/**
 * Student search of the external submission dialog: suggests the course's students while typing, shows those who cannot receive an external result as disabled
 * options with the reason, and shows the selected student as a chip.
 */
@Component({
    selector: 'jhi-external-submission-student-search',
    templateUrl: './external-submission-student-search.component.html',
    imports: [FormsModule, TumAetUiAutoCompleteComponent, TumAetUiChipComponent, TumAetUiFormFieldComponent, ArtemisTranslatePipe],
})
export class ExternalSubmissionStudentSearchComponent {
    private readonly externalSubmissionService = inject(ExternalSubmissionService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exercise = input.required<Exercise>();

    /** The selected student, or undefined once the selection is cleared. */
    readonly studentSelected = output<ExternalSubmissionCandidate | undefined>();

    private readonly autocomplete = viewChild<TumAetUiAutoCompleteComponent>('autocomplete');

    protected readonly minLength = EXTERNAL_SUBMISSION_SEARCH_MIN_LENGTH;
    protected readonly debounceMs = SEARCH_DEBOUNCE_MS;

    protected readonly query = signal('');
    protected readonly suggestions = signal<CandidateOption[]>([]);
    protected readonly selected = signal<CandidateOption | undefined>(undefined);
    protected readonly searchCompleted = signal(false);
    /** How many students match the last search, which can be more than the suggestions show. */
    private readonly totalMatches = signal(0);
    protected readonly errorKey = signal<string | undefined>(undefined);
    /** Whether the field was left at least once, after which a missing selection counts as an error. */
    private readonly wasLeft = signal(false);

    /**
     * The one error the field shows: a failed search or a reason the exercise takes no external submissions comes first, since no student can be selected
     * then; otherwise that a student is missing, once the field was left without one.
     */
    protected readonly shownErrorKey = computed(() => this.errorKey() ?? (this.wasLeft() && !this.selected() ? 'artemisApp.result.studentLoginRequired' : undefined));

    /** The hint below the field: how many characters are needed, or that nothing matched. */
    protected readonly hint = computed((): Hint | undefined => {
        if (this.query().trim().length < this.minLength) {
            return { key: 'artemisApp.externalSubmission.searchTooShort', params: { min: this.minLength } };
        }
        if (this.searchCompleted() && this.suggestions().length === 0) {
            return { key: 'artemisApp.externalSubmission.noResults' };
        }
        return undefined;
    });

    /** Shown at the bottom of the suggestion list rather than as the hint, which the open list would cover. */
    protected readonly moreResults = computed((): Hint | undefined => {
        if (this.searchCompleted() && this.totalMatches() > this.suggestions().length) {
            return { key: 'artemisApp.externalSubmission.moreResults', params: { shown: this.suggestions().length, total: this.totalMatches() } };
        }
        return undefined;
    });

    private readonly searchQuery = new Subject<string>();

    constructor() {
        this.searchQuery
            .pipe(
                switchMap((searchTerm) =>
                    this.externalSubmissionService.searchCandidates(this.exercise(), searchTerm, 0, SEARCH_PAGE_SIZE).pipe(
                        map((page): SearchOutcome => ({ searchTerm, candidates: page.content, totalMatches: page.totalElements })),
                        catchError((error: unknown) => of<SearchOutcome>({ searchTerm, errorKey: this.errorKeyOf(error) })),
                    ),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((outcome) => this.applySearchOutcome(outcome));
    }

    protected onQueryChange(value: unknown): void {
        if (typeof value === 'string' || value === undefined) {
            this.query.set(value ?? '');
            // the suggestions belong to the previous text, so none of them may be selected until the new search answers
            this.suggestions.set([]);
        }
        // an option object is reported here as well when it is selected; selectOption handles it
        this.searchCompleted.set(false);
        this.errorKey.set(undefined);
    }

    protected requestSearch(query: string): void {
        const searchTerm = query.trim();
        if (searchTerm.length < this.minLength) {
            this.suggestions.set([]);
            return;
        }
        this.searchQuery.next(searchTerm);
    }

    protected selectOption(event: TumAetUiAutoCompleteOptionEvent): void {
        const option = this.suggestions().find((suggestion) => suggestion === event.value);
        if (!option || option.disabled) {
            return;
        }
        this.selected.set(option);
        this.studentSelected.emit(option.candidate);
    }

    protected clearSelection(): void {
        this.selected.set(undefined);
        this.query.set('');
        this.suggestions.set([]);
        this.searchCompleted.set(false);
        this.studentSelected.emit(undefined);
    }

    protected onFocusOut(): void {
        this.wasLeft.set(true);
    }

    /** The full label of an option; the option template receives it untyped. */
    protected labelOf(option: unknown): string {
        return typeof option === 'object' && option !== null && 'label' in option && typeof option.label === 'string' ? option.label : '';
    }

    private applySearchOutcome(outcome: SearchOutcome): void {
        if (outcome.searchTerm !== this.query().trim()) {
            // the text changed while the request was running; its own search follows after the debounce
            return;
        }
        if ('errorKey' in outcome) {
            this.suggestions.set([]);
            this.errorKey.set(outcome.errorKey);
            this.autocomplete()?.hide();
            return;
        }
        this.errorKey.set(undefined);
        this.suggestions.set(outcome.candidates.map((candidate) => this.toOption(candidate)));
        this.totalMatches.set(outcome.totalMatches);
        this.searchCompleted.set(true);
    }

    private toOption(candidate: ExternalSubmissionCandidate): CandidateOption {
        const nameAndLogin = candidate.name ? `${candidate.name} (${candidate.login})` : candidate.login;
        if (!candidate.ineligibility) {
            return { label: nameAndLogin, disabled: false, candidate };
        }
        const reason = this.translateService.instant(INELIGIBILITY_KEYS[candidate.ineligibility]);
        return { label: `${nameAndLogin}, ${reason}`, disabled: true, candidate };
    }

    private errorKeyOf(error: unknown): string {
        const serverKey = error instanceof HttpErrorResponse ? error.headers?.get('X-artemisApp-error') : undefined;
        return serverKey && EXERCISE_LEVEL_ERROR_KEYS.includes(serverKey) ? serverKey : 'artemisApp.externalSubmission.searchFailed';
    }
}
