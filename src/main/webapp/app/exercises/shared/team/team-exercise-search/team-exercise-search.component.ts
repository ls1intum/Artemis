import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { combineLatest, merge, Observable, of, Subject } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { orderBy } from 'lodash-es';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-team-exercise-search',
    templateUrl: './team-exercise-search.component.html',
})
export class TeamExerciseSearchComponent implements OnInit {
    @ViewChild('instance', { static: true }) ngbTypeahead: NgbTypeahead;
    focus$ = new Subject<string>();
    click$ = new Subject<string>();

    @Input() course: Course;
    @Input() ignoreExercises: Exercise[];

    @Output() selectExercise = new EventEmitter<Exercise>();
    @Output() searching = new EventEmitter<boolean>();
    @Output() searchQueryTooShort = new EventEmitter<boolean>();
    @Output() searchFailed = new EventEmitter<boolean>();
    @Output() searchNoResults = new EventEmitter<string | undefined>();

    exercise: Exercise;
    exerciseOptions: Exercise[] = [];
    exerciseOptionsLoaded = false;

    inputDisplayValue: string;

    constructor(private courseService: CourseManagementService, private translateService: TranslateService) {}

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {}

    onAutocompleteSelect = (exercise: Exercise) => {
        this.inputDisplayValue = this.searchResultFormatter(exercise);
        this.selectExercise.emit(exercise);
    };

    searchInputFormatter = () => {
        return this.inputDisplayValue;
    };

    searchResultFormatter = (exercise: Exercise): string => {
        const { title, releaseDate } = exercise;
        const date = releaseDate ? releaseDate.format(ArtemisDatePipe.format(this.translateService.currentLang, 'short-date')) : '';
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
        const clicksWithClosedPopup$ = this.click$.pipe(filter(() => !this.ngbTypeahead.isPopupOpen()));
        const inputFocus$ = this.focus$;

        return merge(text$, inputFocus$, clicksWithClosedPopup$).pipe(
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
            .findWithExercises(this.course.id!)
            .pipe(map((courseResponse) => courseResponse.body!.exercises || []))
            .pipe(map((exercises) => exercises.filter((exercise) => exercise.teamMode)))
            .pipe(map((exercises) => exercises.filter((exercise) => !this.ignoreExercises.map((e) => e.id).includes(exercise.id))))
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
