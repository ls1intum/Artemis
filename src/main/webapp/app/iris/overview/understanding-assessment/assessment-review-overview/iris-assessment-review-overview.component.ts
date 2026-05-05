import { Component, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { HttpResponse } from '@angular/common/http';
import { Subscription, finalize, forkJoin, map, switchMap, tap } from 'rxjs';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { IrisAssessmentReviewExerciseComponent } from 'app/iris/overview/understanding-assessment/assessment-review-overview/iris-assessment-review-exercise.component';
import { FormsModule } from '@angular/forms';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { Observable, of } from 'rxjs';
import { take } from 'rxjs/operators';

/**
 * Filter properties for a result
 */
export enum FilterProp {
    ALL = 'All',
    ACCEPTED = 'Accepted',
    REJECTED = 'Rejected',
    REVIEWABLE = 'Reviewable',
    NEEDS_REVIEW = 'NeedsReview',
    MISSING = 'MissingAssessment', // When IrisVerdictReview is undefined or null
}

export type ExerciseViewModel = BaseEntity & {
    readonly exercise: ProgrammingExercise;
    searchedAndFilteredParticipations: ProgrammingExerciseStudentParticipation[];
};

@Component({
    selector: 'jhi-iris-assessment-review-overview',
    templateUrl: './iris-assessment-review-overview.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [
        DataTableComponent,
        ArtemisTranslatePipe,
        FaIconComponent,
        KeyValuePipe,
        TranslateDirective,
        DecimalPipe,
        IrisAssessmentReviewExerciseComponent,
        FormsModule,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgxDatatableModule,
    ],
})
export class IrisAssessmentReviewOverviewComponent implements OnInit {
    readonly FilterProp = FilterProp;
    reviewCriteria: { filterProp: FilterProp } = { filterProp: FilterProp.ALL };
    participationsPerFilter: Map<FilterProp, number> = new Map();
    exercises: ExerciseViewModel[] = [];
    relevantExercises: ExerciseViewModel[] = [];
    course: Course;

    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private participationService = inject(ParticipationService);
    private irisSettingsService = inject(IrisSettingsService);

    protected readonly faSync = faSync;

    isLoading: boolean;
    paramSub: Subscription;
    isSearching = false;
    searchNoResults = false;
    isTransitioning = false;
    lastSearchString = '';

    faFilter = faFilter;

    ngOnInit() {
        this.isLoading = true;

        this.paramSub = this.route.params
            .pipe(
                take(1),
                switchMap((params) => this.courseService.findWithExercises(params['courseId'])),
                map((courseRes) => courseRes.body!),
                switchMap((course) => {
                    this.course = course;

                    const exercises = course.exercises?.filter((e: Exercise) => e.type === ExerciseType.PROGRAMMING && !e.teamMode) ?? [];

                    return forkJoin(
                        exercises.map((exercise) =>
                            this.irisSettingsService.getUncombinedExerciseSettings(exercise.id!).pipe(
                                map((settings) => ({
                                    exercise,
                                    enabled: settings?.irisPromptUserSettings?.enabled,
                                })),
                            ),
                        ),
                    ).pipe(map((results) => results.filter((result) => result.enabled).map((result) => result.exercise)));
                }),
                switchMap((exercises) =>
                    forkJoin(
                        exercises.map((exercise) => {
                            const viewExercise: ExerciseViewModel = {
                                exercise,
                                id: exercise.id,
                                searchedAndFilteredParticipations: [],
                            };

                            return this.participationService.findAllParticipationsNonZeroLatestScoreByExercise(exercise.id!).pipe(
                                map((participationsResponse) => {
                                    this.handleNewParticipations(viewExercise, participationsResponse);

                                    return viewExercise;
                                }),
                            );
                        }),
                    ),
                ),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe((viewExercises) => {
                this.exercises = viewExercises;
                this.updateParticipationsPerFilter();
                this.updateRelevantExercises();
            });
    }

    get allSearchedAndFilteredParticipations(): ProgrammingExerciseStudentParticipation[] {
        return this.exercises.flatMap((e) => e.searchedAndFilteredParticipations ?? []);
    }

    /**
     * Updates the criteria by which to filter results
     * @param newValue New filter prop value
     */
    updateParticipationFilter(newValue: FilterProp) {
        this.isLoading = true;
        setTimeout(() => {
            this.reviewCriteria.filterProp = newValue;
            this.exercises.forEach((e) => this.applySearchAndFilterOnParticipations(e));
            this.updateRelevantExercises();
            this.isLoading = false;
        });
    }

    /**
     * Receives the search text and entities from datatable (here this replaces the native search), filters the participations of each exercise using the search text and returns the resulting participations which will be used by ngbTypeahead.
     *
     * 1. Perform search using the search text
     * 2. Filter participations for nested loop
     * 3. Return results that all matching participations for typeahead
     *
     * @param stream$ stream of searches of the format {text, entities} where entities are the results (here all entities because no filter attributes were passed)
     * @return stream of participations for the autocomplete
     */
    searchAllParticipations = (stream$: Observable<{ text: string; entities: ExerciseViewModel[] }>): Observable<ProgrammingExerciseStudentParticipation[]> => {
        return stream$.pipe(
            switchMap(({ text: loginOrName }) => {
                this.lastSearchString = loginOrName;
                // This is needed for manual nested table search
                this.exercises.forEach((e) => this.applySearchAndFilterOnParticipations(e));
                this.updateRelevantExercises();

                this.searchNoResults = false;
                if (loginOrName.length < 3) {
                    return of([]);
                }
                this.isSearching = true;

                return this.participationSearch(loginOrName).pipe(
                    map((participations) => {
                        this.searchNoResults = participations.length === 0;

                        // Here we eliminate duplicate autocomplete suggestions (in case some entities result in the same suggestion)
                        return Array.from(new Map(participations.map((p) => [this.searchResultFormatter(p), p])).values());
                    }),
                );
            }),
            tap(() => {
                this.isSearching = false;
            }),
        );
    };

    participationSearch(loginOrName: string): Observable<ProgrammingExerciseStudentParticipation[]> {
        const search = loginOrName.toLowerCase();

        const results = this.exercises.flatMap((e) => e.exercise.studentParticipations?.filter((p) => this.filterParticipationByString(p, search)) ?? []);
        return of(results);
    }

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param participation
     */
    searchResultFormatter = (participation: ProgrammingExerciseStudentParticipation) => {
        const name = participation.student?.name ?? 'Unknown';
        const login = participation.student?.login ?? '-';

        return `${name} (${login})`;
    };

    /**
     * Converts a participation object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param participation
     */
    searchTextFromParticipation = (participation: ProgrammingExerciseStudentParticipation): string => {
        return participation.student?.login || '';
    };

    /**
     * Triggers a re-fetch of the results and verdict-review states from the server
     */
    refresh() {
        this.isLoading = true;

        const requests = this.exercises.map((e) =>
            this.participationService.findAllParticipationsNonZeroLatestScoreByExercise(e.exercise.id!).pipe(
                map((res) => ({
                    exercise: e,
                    response: res,
                })),
            ),
        );

        forkJoin(requests).subscribe({
            next: (results) => {
                results.forEach(({ exercise, response }) => {
                    this.handleNewParticipations(exercise, response);
                });
            },
            complete: () => {
                this.isLoading = false;
            },
        });
        this.updateParticipationsPerFilter();
        this.updateRelevantExercises();
    }

    private handleNewParticipations(exercise: ExerciseViewModel, participationsResponse: HttpResponse<Participation[]>) {
        exercise.exercise.studentParticipations = participationsResponse.body ?? [];
        this.applySearchAndFilterOnParticipations(exercise);
    }

    /**
     * Predicate used to filter participations by the current filter prop setting
     * @param participation Participation for which to evaluate the predicate
     * @param filterProp the filter that should be used to determine if the participation should be included or excluded
     */
    private filterParticipationByProp = (participation: ProgrammingExerciseStudentParticipation, filterProp = this.reviewCriteria.filterProp): boolean => {
        switch (filterProp) {
            case FilterProp.REVIEWABLE:
                return participation.irisVerdictReview === IrisVerdictReview.REVIEWABLE;
            case FilterProp.NEEDS_REVIEW:
                return participation.irisVerdictReview === IrisVerdictReview.NEEDS_REVIEW;
            case FilterProp.ACCEPTED:
                return participation.irisVerdictReview === IrisVerdictReview.ACCEPTED;
            case FilterProp.REJECTED:
                return participation.irisVerdictReview === IrisVerdictReview.REJECTED;
            case FilterProp.MISSING:
                return participation.irisVerdictReview === undefined || participation.irisVerdictReview === null;
            case FilterProp.ALL:
            default:
                return true;
        }
    };

    private filterParticipationByString(participation: ProgrammingExerciseStudentParticipation, search = this.lastSearchString): boolean {
        if (search == '') {
            return true;
        }

        const name = participation.student?.name?.toLowerCase() ?? '';
        const login = participation.student?.login?.toLowerCase() ?? '';

        return name.includes(search) || login.includes(search);
    }

    /**
     * Updates the values of the participationsPerFilter Map used for the Filter toggle-dropdown(-menu)
     */
    private updateParticipationsPerFilter() {
        this.participationsPerFilter = new Map<FilterProp, number>();

        this.exercises.forEach((e) => {
            // update Filter Dropdown (menu)
            for (const filter of Object.values(FilterProp)) {
                const count = e.exercise.studentParticipations?.filter((p) => this.filterParticipationByProp(p, filter)).length;

                const previous = this.participationsPerFilter.get(filter) ?? 0;

                this.participationsPerFilter.set(filter, previous + count!);
            }
        });
    }

    private applySearchAndFilterOnParticipations(exercise: ExerciseViewModel) {
        exercise.searchedAndFilteredParticipations =
            exercise.exercise.studentParticipations?.filter((p) => this.filterParticipationByProp(p) && this.filterParticipationByString(p)) ?? [];
    }

    private updateRelevantExercises() {
        this.relevantExercises = this.exercises.filter((e) => e.searchedAndFilteredParticipations?.length > 0);
    }
}
