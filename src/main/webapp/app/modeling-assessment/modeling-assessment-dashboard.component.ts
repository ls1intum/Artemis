import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute, Router } from '@angular/router';
import { Exercise, ExerciseType } from '../entities/exercise';
import { ExerciseService } from 'app/entities/exercise';
import { Course, CourseService } from '../entities/course';
import { ResultDetailComponent, ResultService } from 'app/entities/result';
import { DifferencePipe } from 'angular2-moment';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from '../entities/result';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from '../core';
import { Submission } from '../entities/submission';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { DiagramType, ModelingExercise } from 'app/entities/modeling-exercise';
import { ModelingAssessmentService } from 'app/modeling-assessment/modeling-assessment.service';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './modeling-assessment-dashboard.component.html',
    providers: [JhiAlertService, ModelingAssessmentService]
})
export class ModelingAssessmentDashboardComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly MODELING = ExerciseType.MODELING;
    readonly CLASS_DIAGRAM = DiagramType.ClassDiagram;

    course: Course;
    modelingExercise: ModelingExercise;
    paramSub: Subscription;
    predicate: string;
    reverse: boolean;
    nextOptimalSubmissionIds: number[] = [];

    // all available submissions
    submissions: ModelingSubmission[];
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
        private accountService: AccountService
    ) {
        this.reverse = false;
        this.predicate = 'id';
        this.submissions = [];
        this.optimalSubmissions = [];
        this.otherSubmissions = [];
        this.canOverrideAssessments = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
    }

    ngOnInit() {
        this.accountService.identity().then(user => {
            this.userId = user.id;
        });
        this.paramSub = this.route.params.subscribe(params => {
            this.courseService.find(params['courseId']).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body;
            });
            this.exerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<Exercise>) => {

                if (res.body.type === this.MODELING) {
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
        this.modelingSubmissionService
            .getModelingSubmissionsForExercise(this.modelingExercise.id, { submittedOnly: true })
            .subscribe((res: HttpResponse<ModelingSubmission[]>) => {
                // only use submissions that have already been submitted (this makes sure that unsubmitted submissions are not shown
                // the server should have filtered these submissions already
                this.submissions = res.body.filter(submission => submission.submitted);
                this.submissions.forEach(submission => {
                    if (submission.result) {
                        // reconnect some associations
                        submission.result.submission = submission;
                        submission.result.participation = submission.participation;
                        submission.participation.results = [submission.result];
                    }
                });
                this.filterSubmissions(forceReload);
                this.assessedSubmissions = this.submissions.filter(
                    submission => submission.result && submission.result.completionDate && submission.result.score
                ).length;
            });
    }

    /**
     * Check if nextOptimalSubmissionIds are needed then applyFilter
     *
     * @param {boolean} forceReload force REST call to update nextOptimalSubmissionIds
     */
    filterSubmissions(forceReload: boolean) {
        if (this.modelingExercise.diagramType === this.CLASS_DIAGRAM && (this.nextOptimalSubmissionIds.length < 3 || forceReload)) {
            this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise.id).subscribe(optimal => {
                this.nextOptimalSubmissionIds = optimal.body.map((submission: Submission) => submission.id);
                this.applyFilter();
            });
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
            submission.optimal = this.nextOptimalSubmissionIds.includes(submission.id) &&
                (!(submission.result && submission.result.assessor) ||
                    (submission.result && submission.result.assessor && submission.result.assessor.id === this.userId));
        });
        this.optimalSubmissions = this.submissions.filter(submission => {
            return submission.optimal;
        });
        this.otherSubmissions = this.submissions.filter(submission => {
            return !submission.optimal;
        });
    }

    durationString(completionDate: Date, initializationDate: Date) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    showDetails(result: Result) {
        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = result;
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
     *
     * @param {number} attempts Count the attempts to reduce frequency on repeated failure (network errors)
     */
    assessNextOptimal(attempts: number) {
        if (attempts > 3) {
            this.busy = false;
            this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
            return;
        }
        this.busy = true;
        if (this.nextOptimalSubmissionIds.length === 0) {
            setTimeout(() => {
                this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise.id).subscribe(optimal => {
                    this.nextOptimalSubmissionIds = optimal.body.map((submission: Submission) => submission.id);
                    this.assessNextOptimal(attempts + 1);
                });
            }, 500 + 1000 * attempts);
        } else {
            const randomInt = Math.floor(Math.random() * this.nextOptimalSubmissionIds.length);
            this.router.navigate(['modeling-exercise', this.modelingExercise.id, 'submissions', this.nextOptimalSubmissionIds[randomInt], 'assessment']);
        }
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    callback() {}
}
