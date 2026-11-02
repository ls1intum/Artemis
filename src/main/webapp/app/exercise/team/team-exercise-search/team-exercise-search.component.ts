import { Component, DestroyRef, inject, input, output, signal, viewChild } from '@angular/core';
import { Observable, Subject, combineLatest, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { TumAetUiAutoCompleteComponent, TumAetUiAutoCompleteOptionEvent } from '@tumaet/ui-angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { orderBy } from 'lodash-es';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-team-exercise-search',
    templateUrl: './team-exercise-search.component.html',
    imports: [FormsModule, TumAetUiAutoCompleteComponent, ArtemisTranslatePipe],
})
export class TeamExerciseSearchComponent {
    private courseService = inject(CourseManagementService);
    private translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly autocomplete = viewChild<TumAetUiAutoCompleteComponent>('autocomplete');

    readonly course = input.required<Course>();
    readonly ignoreExercises = input<Exercise[]>([]);

    readonly selectExercise = output<Exercise>();
    readonly searching = output<boolean>();
    readonly searchQueryTooShort = output<boolean>();
    readonly searchFailed = output<boolean>();
    readonly searchNoResults = output<string | undefined>();

    exercise?: Exercise;
    exerciseOptions: Exercise[] = [];
    exerciseOptionsLoaded = false;

    private readonly displayValue = signal('');
    get inputDisplayValue(): string {
        return this.displayValue();
    }
    set inputDisplayValue(value: string) {
        this.displayValue.set(value);
    }

    readonly suggestions = signal<{ label: string; value: Exercise; disabled: boolean }[]>([]);
    private readonly searchQuery = new Subject<string>();

    constructor() {
        this.onSearch(this.searchQuery)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((values) => {
                this.suggestions.set(values.map((value) => ({ label: this.searchResultFormatter(value), value, disabled: false })));
            });
    }

    requestSearch(query: string): void {
        this.searchQuery.next(query);
    }

    selectOption(event: TumAetUiAutoCompleteOptionEvent): void {
        const option = this.suggestions().find((suggestion) => suggestion === event.value);
        if (option && !option.disabled) {
            this.onAutocompleteSelect(option.value);
        }
    }

    onAutocompleteSelect = (exercise: Exercise) => {
        this.inputDisplayValue = this.searchResultFormatter(exercise);
        this.selectExercise.emit(exercise);
    };

    searchInputFormatter = () => {
        return this.inputDisplayValue;
    };

    searchResultFormatter = (exercise: Exercise): string => {
        const { title, releaseDate } = exercise;
        const date = releaseDate ? releaseDate.format(ArtemisDatePipe.format(this.translateService.getCurrentLang() ?? 'en', 'short-date')) : '';
        return title + (releaseDate ? ` (${date})` : '');
    };

    /**
     * Check if given searchTerm is included in given exercise
     * @param {string} searchTerm - Term to search for
     * @param {Exercise} exercise - Exercise to search through
     */
    searchMatchesExercise(searchTerm: string, exercise: Exercise) {
        return exercise.title!.toLowerCase().includes(searchTerm.toLowerCase());
    }

    onSearch = (text$: Observable<string>) => {
        return text$.pipe(
            tap(() => {
                this.searchNoResults.emit(undefined);
            }),
            switchMap((searchTerm) => {
                this.searchFailed.emit(false);
                this.searching.emit(true);
                // If exercise options have already been loaded once, do not load them again and reuse the already loaded ones
                const exerciseOptionsSource$ = this.exerciseOptionsLoaded ? of(this.exerciseOptions) : this.loadExerciseOptions();
                return combineLatest([of(searchTerm), exerciseOptionsSource$]);
            }),
            tap(() => this.searching.emit(false)),
            switchMap(([searchTerm, exerciseOptions]) => {
                // Filter list of all exercise options by the search term
                const match = (exercise: Exercise) => this.searchMatchesExercise(searchTerm, exercise);
                return combineLatest([of(searchTerm), of(!exerciseOptions ? exerciseOptions : exerciseOptions.filter(match))]);
            }),
            tap(([searchTerm, exerciseOptions]) => {
                if (exerciseOptions && exerciseOptions.length === 0) {
                    this.searchNoResults.emit(searchTerm);
                }
            }),
            map(([, exerciseOptions]) => exerciseOptions || []),
            map((exerciseOptions) => orderBy(exerciseOptions, ['releaseDate', 'id'])),
        );
    };

    /**
     * Load options of team exercise
     */
    loadExerciseOptions() {
        return this.courseService
            .findWithExercises(this.course().id!)
            .pipe(map((courseResponse) => courseResponse.body!.exercises || []))
            .pipe(map((exercises) => exercises.filter((exercise) => exercise.teamMode)))
            .pipe(
                map((exercises) =>
                    exercises.filter(
                        (exercise) =>
                            !this.ignoreExercises()
                                .map((e) => e.id)
                                .includes(exercise.id),
                    ),
                ),
            )
            .pipe(
                tap((exerciseOptions) => {
                    this.exerciseOptions = exerciseOptions;
                    this.exerciseOptionsLoaded = true;
                }),
            )
            .pipe(
                catchError(() => {
                    this.exerciseOptionsLoaded = false;
                    this.searchFailed.emit(true);
                    return of(null);
                }),
            );
    }
}
