import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { getLatestSubmissionResult, setLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SortService } from 'app/shared/service/sort.service';
import { Authority } from 'app/shared/constants/authority.constants';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './modeling-assessment-dashboard.component.html',
    providers: [],
})
export class ModelingAssessmentDashboardComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    ExerciseType = ExerciseType;
    AssessmentType = AssessmentType;

    course: Course;
    modelingExercise: ModelingExercise;
    paramSub: Subscription;
    predicate: string;
    reverse: boolean;
    nextOptimalSubmissionIds: number[] = [];
    numberOfCorrectionrounds = 1;

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
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private resultService: ResultService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private modalService: NgbModal,
        private eventManager: JhiEventManager,
        private accountService: AccountService,
        private translateService: TranslateService,
        private sortService: SortService,
    ) {
        this.reverse = false;
        this.predicate = 'id';
        this.submissions = [];
        this.filteredSubmissions = [];
        this.optimalSubmissions = [];
        this.otherSubmissions = [];
        this.canOverrideAssessments = this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR]);
        translateService.get('modelingAssessmentEditor.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    ngOnInit() {
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseService.find(params['courseId']).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body!;
            });
            this.exerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<Exercise>) => {
                if (res.body!.type === ExerciseType.MODELING) {
                    this.modelingExercise = res.body as ModelingExercise;
                    this.getSubmissions(true);
                    this.numberOfCorrectionrounds = this.modelingExercise.exerciseGroup ? this.modelingExercise!.exerciseGroup.exam!.numberOfCorrectionRoundsInExam! : 1;
                    this.setPermissions();
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
            .getModelingSubmissionsForExerciseByCorrectionRound(this.modelingExercise.id!, { submittedOnly: true })
            .subscribe((res: HttpResponse<ModelingSubmission[]>) => {
                // only use submissions that have already been submitted (this makes sure that unsubmitted submissions are not shown
                // the server should have filtered these submissions already
                this.submissions = res.body!.filter((submission) => submission.submitted);
                this.submissions.forEach((submission) => {
                    const tmpResult = getLatestSubmissionResult(submission);
                    if (tmpResult) {
                        // reconnect some associations
                        submission.latestResult = tmpResult;
                        tmpResult!.submission = submission;
                        tmpResult!.participation = submission.participation;
                        if (submission.participation) {
                            submission.participation.results = [tmpResult!];
                        }
                    }
                });
                this.filteredSubmissions = this.submissions;
                this.filterSubmissions(forceReload);
                this.assessedSubmissions = this.submissions.filter((submission) => {
                    const result = getLatestSubmissionResult(submission);
                    setLatestSubmissionResult(submission, result);
                    return !!result;
                }).length;
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
        if (this.modelingExercise.assessmentType === AssessmentType.SEMI_AUTOMATIC && (this.nextOptimalSubmissionIds.length < 3 || forceReload)) {
            this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise.id!).subscribe(
                (optimal: number[]) => {
                    this.nextOptimalSubmissionIds = optimal;
                    this.applyFilter();
                },
                () => {
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
        this.submissions.forEach((submission) => {
            const tmpResult = getLatestSubmissionResult(submission);
            submission.optimal =
                this.nextOptimalSubmissionIds.includes(submission.id!) &&
                (!(tmpResult && tmpResult!.assessor) || (tmpResult && tmpResult!.assessor && tmpResult!.assessor!.id === this.userId));
        });
        this.optimalSubmissions = this.filteredSubmissions.filter((submission) => {
            return submission.optimal;
        });
        this.otherSubmissions = this.filteredSubmissions.filter((submission) => {
            return !submission.optimal;
        });
    }

    refresh() {
        this.getSubmissions(true);
    }

    /**
     * Reset optimality attribute of models
     */
    resetOptimality() {
        if (this.modelingExercise.assessmentType === AssessmentType.SEMI_AUTOMATIC) {
            this.modelingAssessmentService.resetOptimality(this.modelingExercise.id!).subscribe(() => {
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
            this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise.id!).subscribe(
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
        this.router.navigate([
            '/course-management',
            this.course.id,
            'modeling-exercises',
            this.modelingExercise.id,
            'submissions',
            this.nextOptimalSubmissionIds[randomInt],
            'assessment',
        ]);
    }

    private setPermissions() {
        if (this.modelingExercise.course) {
            this.modelingExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.modelingExercise.course!);
        } else {
            this.modelingExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.modelingExercise.exerciseGroup?.exam?.course!);
        }
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     */
    cancelAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.modelingAssessmentService.cancelAssessment(submission.id!).subscribe(() => {
                this.refresh();
            });
        }
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    public sortRows() {
        this.sortService.sortByProperty(this.otherSubmissions, this.predicate, this.reverse);
    }

    /**
     * get the link for the assessment of a specific submission of the current exercise
     * @param submissionId
     */
    getAssessmentLink(submissionId: number) {
        if (this.modelingExercise.exerciseGroup) {
            return [
                '/course-management',
                this.modelingExercise.exerciseGroup.exam?.course?.id,
                'modeling-exercises',
                this.modelingExercise.id,
                'submissions',
                submissionId,
                'assessment',
            ];
        } else {
            return ['/course-management', this.modelingExercise.course?.id, 'modeling-exercises', this.modelingExercise.id, 'submissions', submissionId, 'assessment'];
        }
    }
}
