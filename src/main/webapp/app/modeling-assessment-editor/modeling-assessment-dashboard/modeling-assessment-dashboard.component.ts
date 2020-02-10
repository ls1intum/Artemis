import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Exercise, ExerciseService, ExerciseType } from 'app/entities/exercise';
import { UMLDiagramType } from '@ls1intum/apollon';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { Subscription } from 'rxjs';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ActivatedRoute, Router } from '@angular/router';
import { ResultService } from 'app/entities/result';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { HttpResponse } from '@angular/common/http';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { DifferencePipe } from 'ngx-moment';
import { TranslateService } from '@ngx-translate/core';
import { Submission } from 'app/entities/submission';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './modeling-assessment-dashboard.component.html',
    providers: [JhiAlertService, ModelingAssessmentService],
})
export class ModelingAssessmentDashboardComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly MODELING = ExerciseType.MODELING;
    readonly CLASS_DIAGRAM = UMLDiagramType.ClassDiagram;

    course: Course;
    modelingExercise: ModelingExercise;
    paramSub: Subscription;
    predicate: string;
    reverse: boolean;
    nextOptimalSubmissionIds: number[] = [];

    private cancelConfirmationText: string;

    // all available submissions
    submissions: ModelingSubmission[];
    filteredSubmissions: ModelingSubmission[];
    optimalSubmissions: ModelingSubmission[];
    // non optimal submissions
    otherSubmissions: ModelingSubmission[];

    eventSubscriber: Subscription;
    assessedSubmissions: number;
    allSubmissionsVisible: boolean;
    busy: boolean;
    userId: number;
    canOverrideAssessments: boolean;

    constructor(
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private router: Router,
        private momentDiff: DifferencePipe,
        private courseService: CourseService,
        private exerciseService: ExerciseService,
        private resultService: ResultService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private modalService: NgbModal,
        private eventManager: JhiEventManager,
        private accountService: AccountService,
        private translateService: TranslateService,
    ) {
        this.reverse = false;
        this.predicate = 'id';
        this.submissions = [];
        this.filteredSubmissions = [];
        this.optimalSubmissions = [];
        this.otherSubmissions = [];
        this.canOverrideAssessments = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
        translateService.get('modelingAssessmentEditor.messages.confirmCancel').subscribe(text => (this.cancelConfirmationText = text));
    }

    ngOnInit() {
        this.accountService.identity().then(user => {
            this.userId = user!.id!;
        });
        this.paramSub = this.route.params.subscribe(params => {
            this.courseService.find(params['courseId']).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body!;
            });
            this.exerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<Exercise>) => {
                if (res.body!.type === this.MODELING) {
                    this.modelingExercise = res.body as ModelingExercise;
                    this.getSubmissions(true);
                } else {
                    // TODO: error message if this is not a modeling exercise
                }
            });
        });
        this.registerChangeInResults();
    }

    registerChangeInResults() {
        this.eventSubscriber = this.eventManager.subscribe('resultListModification', () => this.getSubmissions(true));
    }

    /**
     * Get all results for the current modeling exercise, this includes information about all submitted models ( = submissions)
     *
     * @param {boolean} forceReload force REST call to update nextOptimalSubmissionIds
     */
    getSubmissions(forceReload: boolean) {
        this.modelingSubmissionService.getModelingSubmissionsForExercise(this.modelingExercise.id, { submittedOnly: true }).subscribe((res: HttpResponse<ModelingSubmission[]>) => {
            // only use submissions that have already been submitted (this makes sure that unsubmitted submissions are not shown
            // the server should have filtered these submissions already
            this.submissions = res.body!.filter(submission => submission.submitted);
            this.submissions.forEach(submission => {
                if (submission.result) {
                    // reconnect some associations
                    submission.result.submission = submission;
                    submission.result.participation = submission.participation;
                    submission.participation.results = [submission.result];
                }
            });
            this.filteredSubmissions = this.submissions;
            this.filterSubmissions(forceReload);
            this.assessedSubmissions = this.submissions.filter(submission => submission.result && submission.result.completionDate && submission.result.score).length;
        });
    }

    updateFilteredSubmissions(filteredSubmissions: Submission[]) {
        this.filteredSubmissions = filteredSubmissions as ModelingSubmission[];
        this.applyFilter();
    }

    /**
     * Check if nextOptimalSubmissionIds are needed then applyFilter
     *
     * @param {boolean} forceReload force REST call to update nextOptimalSubmissionIds
     */
    filterSubmissions(forceReload: boolean) {
        if (this.modelingExercise.diagramType === this.CLASS_DIAGRAM && (this.nextOptimalSubmissionIds.length < 3 || forceReload)) {
            this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise.id).subscribe(
                (optimal: number[]) => {
                    this.nextOptimalSubmissionIds = optimal;
                    this.applyFilter();
                },
                error => {
                    this.applyFilter();
                },
            );
        } else {
            this.applyFilter();
        }
    }

    /**
     * Mark results as optimal and split them up in all, optimal and not optimal sets
     */
    applyFilter() {
        // A submission is optimal if it is part of nextOptimalSubmissionIds and (nobody is currently assessing it or you are currently assessing it)
        this.submissions.forEach(submission => {
            submission.optimal =
                this.nextOptimalSubmissionIds.includes(submission.id) &&
                (!(submission.result && submission.result.assessor) || (submission.result && submission.result.assessor && submission.result.assessor.id === this.userId));
        });
        this.optimalSubmissions = this.filteredSubmissions.filter(submission => {
            return submission.optimal;
        });
        this.otherSubmissions = this.filteredSubmissions.filter(submission => {
            return !submission.optimal;
        });
    }

    durationString(completionDate: Date, initializationDate: Date) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    refresh() {
        this.getSubmissions(true);
    }

    /**
     * Reset optimality attribute of models
     */
    resetOptimality() {
        if (this.modelingExercise.diagramType === this.CLASS_DIAGRAM) {
            this.modelingAssessmentService.resetOptimality(this.modelingExercise.id).subscribe(() => {
                this.filterSubmissions(true);
            });
        }
    }

    makeAllSubmissionsVisible() {
        if (!this.busy) {
            this.allSubmissionsVisible = true;
        }
    }

    /**
     * Select the next optimal submission to assess or otherwise trigger the REST call
     */
    assessNextOptimal() {
        this.busy = true;
        if (this.nextOptimalSubmissionIds.length === 0) {
            this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise.id).subscribe(
                (optimal: number[]) => {
                    this.busy = false;
                    if (optimal.length === 0) {
                        this.jhiAlertService.clear();
                        this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
                    } else {
                        this.nextOptimalSubmissionIds = optimal;
                        this.navigateToNextRandomOptimalSubmission();
                    }
                },
                () => {
                    this.busy = false;
                    this.jhiAlertService.clear();
                    this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
                },
            );
        } else {
            this.navigateToNextRandomOptimalSubmission();
        }
    }

    private navigateToNextRandomOptimalSubmission() {
        const randomInt = Math.floor(Math.random() * this.nextOptimalSubmissionIds.length);
        this.router.onSameUrlNavigation = 'reload';
        this.router.navigate(['modeling-exercise', this.modelingExercise.id, 'submissions', this.nextOptimalSubmissionIds[randomInt], 'assessment']);
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     */
    cancelAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.modelingAssessmentService.cancelAssessment(submission.id).subscribe(() => {
                this.refresh();
            });
        }
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    callback() {}
}
