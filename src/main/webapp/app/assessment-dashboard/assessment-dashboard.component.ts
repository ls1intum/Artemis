import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute, Router } from '@angular/router';
import { Exercise, ExerciseType } from '../entities/exercise';
import { ExerciseService } from '../entities/exercise/exercise.service';
import { Course, CourseService } from '../entities/course';
import { ResultService } from '../entities/result/result.service';
import { DifferencePipe } from 'angular2-moment';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from '../entities/result';
import { ResultDetailComponent } from '../entities/result/result-detail.component';
import { ModelingAssessmentService } from '../entities/modeling-assessment/modeling-assessment.service';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from '../core';
import { Submission } from '../entities/submission';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './assessment-dashboard.component.html',
    providers: [JhiAlertService, ModelingAssessmentService]
})
export class AssessmentDashboardComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    course: Course;
    exercise: Exercise;
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
                this.exercise = res.body;
                this.getSubmissions(true);
            });
        });
        this.registerChangeInResults();
    }

    registerChangeInResults() {
        this.eventSubscriber = this.eventManager.subscribe('resultListModification', () => this.getSubmissions(true));
    }

    /**
     * Get all results for the current exercise, this includes information about all submitted models ( = submissions)
     *
     * @param {boolean} forceReload force REST call to update nextOptimalSubmissionIds
     */
    getSubmissions(forceReload: boolean) {
        this.modelingSubmissionService
            .getModelingSubmissionsForExercise(this.exercise.id, { submittedOnly: true })
            .subscribe((res: HttpResponse<ModelingSubmission[]>) => {
                // only use submissions that have already been submitted (this makes sure that unsubmitted submissions are not shown
                // the server should have filtered these submissions alreadyart
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
        if (this.nextOptimalSubmissionIds.length < 3 || forceReload) {
            this.modelingAssessmentService.getOptimalSubmissions(this.exercise.id).subscribe(optimal => {
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
            submission.optimal =
                this.nextOptimalSubmissionIds.includes(submission.id) &&
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
        this.modelingAssessmentService.resetOptimality(this.exercise.id).subscribe(() => {
            this.filterSubmissions(true);
        });
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
                this.modelingAssessmentService.getOptimalSubmissions(this.exercise.id).subscribe(optimal => {
                    this.nextOptimalSubmissionIds = optimal.body.map((submission: Submission) => submission.id);
                    this.assessNextOptimal(attempts + 1);
                });
            }, 500 + 1000 * attempts);
        } else {
            const randomInt = Math.floor(Math.random() * this.nextOptimalSubmissionIds.length);
            this.router.navigate(['apollon-diagrams', 'exercise', this.exercise.id, this.nextOptimalSubmissionIds[randomInt], 'tutor']);
        }
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    callback() {}
}
