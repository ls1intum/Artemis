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
                    this.participations = participationsResponse.body!;
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

    updateResults() {
        this.participations = this.sortByPipe.transform([...this.participations], this.resultCriteria.sortProp.field, this.resultCriteria.sortProp.order === SortOrder.ASC);
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
