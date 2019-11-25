import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { Participation } from './participation.model';
import { ParticipationService } from './participation.service';
import { ActivatedRoute } from '@angular/router';
import { areManualResultsAllowed, Exercise, ExerciseType } from '../exercise';
import { ExerciseService } from 'app/entities/exercise';
import { HttpErrorResponse } from '@angular/common/http';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { SortByPipe } from 'app/components/pipes';
import { ColumnMode, SortType } from '@swimlane/ngx-datatable';
import { debounceTime, distinctUntilChanged, map, tap } from 'rxjs/operators';
import { Observable } from 'rxjs';

enum SortOrder {
    ASC = 'asc',
    DESC = 'desc',
}

enum SortIcon {
    NONE = 'sort',
    ASC = 'sort-up',
    DESC = 'sort-down',
}

const SortOrderIcon = {
    [SortOrder.ASC]: SortIcon.ASC,
    [SortOrder.DESC]: SortIcon.DESC,
};

type SortProp = {
    field: string;
    order: SortOrder;
};

const resultsPerPageCacheKey = 'exercise-participation-results-per-age';

@Component({
    selector: 'jhi-participation',
    templateUrl: './participation.component.html',
    styleUrls: ['participation.component.scss'],
})
export class ParticipationComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;

    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    PAGING_VALUES = [10, 20, 50, 100, 200, 500, 1000, 2000];
    DEFAULT_PAGING_VALUE = 50;

    ColumnMode = ColumnMode;
    SortType = SortType;

    participations: StudentParticipation[];
    allParticipations: StudentParticipation[];
    eventSubscriber: Subscription;
    paramSub: Subscription;
    exercise: Exercise;
    predicate: string;
    reverse: boolean;
    newManualResultAllowed: boolean;

    hasLoadedPendingSubmissions = false;
    presentationScoreEnabled = false;

    resultCriteria: {
        textSearch: string[];
        sortProp: SortProp;
    };

    resultsPerPage: number;
    isLoading: boolean;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private exerciseService: ExerciseService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private sortByPipe: SortByPipe,
    ) {
        this.reverse = true;
        this.predicate = 'id';
        this.resultCriteria = {
            textSearch: [],
            sortProp: { field: 'id', order: SortOrder.ASC },
        };
    }

    ngOnInit() {
        this.loadAll();
        this.registerChangeInParticipations();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    loadAll() {
        this.resultsPerPage = this.getCachedResultsPerPage();
        this.paramSub = this.route.params.subscribe(params => {
            this.isLoading = true;
            this.hasLoadedPendingSubmissions = false;
            this.exerciseService.find(params['exerciseId']).subscribe(exerciseResponse => {
                this.exercise = exerciseResponse.body!;
                this.participationService.findAllParticipationsByExercise(params['exerciseId'], true).subscribe(participationsResponse => {
                    this.allParticipations = participationsResponse.body!;
                    this.updateResults();
                    this.isLoading = false;
                });
                if (this.exercise.type === this.PROGRAMMING) {
                    this.programmingSubmissionService.getSubmissionStateOfExercise(this.exercise.id).subscribe(() => (this.hasLoadedPendingSubmissions = true));
                }
                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
                this.presentationScoreEnabled = this.checkPresentationScoreConfig();
            });
        });
    }

    trackId(index: number, item: Participation) {
        return item.id;
    }

    registerChangeInParticipations() {
        this.eventSubscriber = this.eventManager.subscribe('participationListModification', () => this.loadAll());
    }

    checkPresentationScoreConfig(): boolean {
        if (!this.exercise.course) {
            return false;
        }
        return this.exercise.isAtLeastTutor && this.exercise.course.presentationScore !== 0 && this.exercise.presentationScoreEnabled;
    }

    addPresentation(participation: StudentParticipation) {
        if (!this.presentationScoreEnabled) {
            return;
        }
        participation.presentationScore = 1;
        this.participationService.update(participation).subscribe(
            () => {},
            () => {
                this.jhiAlertService.error('artemisApp.participation.addPresentation.error');
            },
        );
    }

    removePresentation(participation: StudentParticipation) {
        if (!this.presentationScoreEnabled) {
            return;
        }
        participation.presentationScore = 0;
        this.participationService.update(participation).subscribe(
            () => {},
            () => {
                this.jhiAlertService.error('artemisApp.participation.removePresentation.error');
            },
        );
    }

    /**
     * Deletes participation
     * @param participationId the id of the participation that we want to delete
     * @param $event passed from delete dialog to represent if checkboxes were checked
     */
    deleteParticipation(participationId: number, $event: { [key: string]: boolean }) {
        const deleteBuildPlan = $event.deleteBuildPlan ? $event.deleteBuildPlan : false;
        const deleteRepository = $event.deleteRepository ? $event.deleteRepository : false;
        this.participationService.delete(participationId, { deleteBuildPlan, deleteRepository }).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'participationListModification',
                    content: 'Deleted an participation',
                });
            },
            error => this.onError(error),
        );
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}

    /**
     * Filter the given results by the provided search words.
     * Returns results that match any of the provides search words, if searchWords is empty returns all results.
     *
     * @param searchWords list of student logins or names.
     * @param participation StudentParticipation
     */
    filterResultByTextSearch = (searchWords: string[], participation: StudentParticipation) => {
        const searchableFields = [participation.student.login, participation.student.name].filter(Boolean) as string[];
        // When no search word is inputted, we return all results.
        if (!searchWords.length) {
            return true;
        }
        // Otherwise we do a fuzzy search on the inputted search words.
        return searchableFields.some(field => searchWords.some(word => word && field.toLowerCase().includes(word.toLowerCase())));
    };

    updateResults() {
        const participations = this.allParticipations.filter((participation: StudentParticipation) => this.filterResultByTextSearch(this.resultCriteria.textSearch, participation));
        this.participations = this.sortByPipe.transform(participations, this.resultCriteria.sortProp.field, this.resultCriteria.sortProp.order === SortOrder.DESC);
    }

    private invertSort = (order: SortOrder) => {
        return order === SortOrder.ASC ? SortOrder.DESC : SortOrder.ASC;
    };

    /**
     * Returns the Font Awesome icon name for a column header's sorting icon
     * based on the currently active sortProp field and order.
     *
     * @param field Result field
     */
    iconForSortPropField(field: string) {
        if (this.resultCriteria.sortProp.field !== field) {
            return SortIcon.NONE;
        }
        return SortOrderIcon[this.resultCriteria.sortProp.order];
    }

    /**
     * Sets the selected sort field, then updates the available results in the UI.
     * Toggles the order direction (asc, desc) when the field has not changed.
     *
     * @param field Result field
     */
    onSort(field: string) {
        const sameField = this.resultCriteria.sortProp && this.resultCriteria.sortProp.field === field;
        const order = sameField ? this.invertSort(this.resultCriteria.sortProp.order) : SortOrder.ASC;
        this.resultCriteria.sortProp = { field, order };
        this.updateResults();
    }

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param participation
     */
    searchResultFormatter = (participation: StudentParticipation) => {
        const { login, name } = participation.student;
        return `${login} (${name})`;
    };

    searchInputFormatter = () => {
        return this.resultCriteria.textSearch.join(', ');
    };

    /**
     * Splits the provides search words by comma and updates the autocompletion overlay.
     * Also updates the available results in the UI.
     *
     * @param text$ stream of text input.
     */
    onSearch = (text$: Observable<string>) => {
        return text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            map(text => {
                const searchWords = text.split(',').map(word => word.trim());
                // When the result field is cleared, we translate the resulting empty string to an empty array (otherwise no results would be found).
                return searchWords.length === 1 && !searchWords[0] ? [] : searchWords;
            }),
            // For available results in table.
            tap(searchWords => {
                this.resultCriteria.textSearch = searchWords;
                this.updateResults();
            }),
            // For autocomplete.
            map((searchWords: string[]) => {
                // We only execute the autocomplete for the last keyword in the provided list.
                const lastSearchWord = searchWords.length ? searchWords[searchWords.length - 1] : null;
                // Don't execute autocomplete for less then two inputted characters.
                if (!lastSearchWord || lastSearchWord.length < 3) {
                    return false;
                }
                return this.participations.filter(participation => {
                    const searchableFields = [participation.student.login, participation.student.name].filter(Boolean) as string[];
                    return searchableFields.some(value => value.toLowerCase().includes(lastSearchWord.toLowerCase()) && value.toLowerCase() !== lastSearchWord.toLowerCase());
                });
            }),
        );
    };

    onAutocompleteSelect = (participation: StudentParticipation) => {
        this.resultCriteria.textSearch[this.resultCriteria.textSearch.length - 1] = participation.student.login!;
        this.updateResults();
    };

    getCachedResultsPerPage = () => {
        const cachedValue = localStorage.getItem(resultsPerPageCacheKey);
        return cachedValue ? parseInt(cachedValue, 10) : this.DEFAULT_PAGING_VALUE;
    };

    setResultsPerPage = (paging: number) => {
        this.isLoading = true;
        setTimeout(() => {
            this.resultsPerPage = paging;
            this.isLoading = false;
        }, 500);
        localStorage.setItem(resultsPerPageCacheKey, paging.toString());
    };
}
