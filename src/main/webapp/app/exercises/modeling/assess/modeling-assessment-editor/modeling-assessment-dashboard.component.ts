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
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';

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
    exercise: ModelingExercise;
    paramSub: Subscription;
    predicate: string;
    reverse: boolean;
    nextOptimalSubmissionIds: number[] = [];
    courseId: number;
    examId: number;
    exerciseId: number;
    exerciseGroupId: number;
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
            this.courseId = params['courseId'];
            this.courseService.find(this.courseId).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body!;
            });
            this.exerciseId = params['exerciseId'];
            this.exerciseService.find(this.exerciseId).subscribe((res: HttpResponse<Exercise>) => {
                if (res.body!.type === ExerciseType.MODELING) {
                    this.exercise = res.body as ModelingExercise;
                    this.courseId = this.exercise.course ? this.exercise.course.id! : this.exercise.exerciseGroup!.exam!.course!.id!;
                    this.getSubmissions(true);
                    this.numberOfCorrectionrounds = this.exercise.exerciseGroup ? this.exercise!.exerciseGroup.exam!.numberOfCorrectionRoundsInExam! : 1;
                    this.setPermissions();
                } else {
                    // TODO: error message if this is not a modeling exercise
                }
            });

            this.examId = params['examId'];
            this.exerciseGroupId = params['exerciseGroupId'];
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
            .getModelingSubmissionsForExerciseByCorrectionRound(this.exercise.id!, { submittedOnly: true })
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
        if (this.exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC && (this.nextOptimalSubmissionIds.length < 3 || forceReload)) {
            this.modelingAssessmentService.getOptimalSubmissions(this.exercise.id!).subscribe(
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
        if (this.exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC) {
            this.modelingAssessmentService.resetOptimality(this.exercise.id!).subscribe(() => {
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
    assessNextOptimal(): void {
        this.busy = true;
        if (this.nextOptimalSubmissionIds.length === 0) {
            this.modelingAssessmentService.getOptimalSubmissions(this.exercise.id!).subscribe(
                (optimal: number[]) => {
                    this.busy = false;
                    if (optimal.length === 0) {
                        this.jhiAlertService.clear();
                        this.jhiAlertService.info('artemisApp.assessmentDashboard.noSubmissionFound');
                    } else {
                        this.nextOptimalSubmissionIds = optimal;
                        this.navigateToNextRandomOptimalSubmission();
                    }
                },
                () => {
                    this.busy = false;
                    this.jhiAlertService.clear();
                    this.jhiAlertService.info('artemisApp.assessmentDashboard.noSubmissionFound');
                },
            );
        } else {
            this.navigateToNextRandomOptimalSubmission();
        }
    }

    getAssessmentRouterLink(submissionId: number): string[] {
        return getLinkToSubmissionAssessment(ExerciseType.MODELING, this.courseId, this.exerciseId, submissionId, this.examId, this.exerciseGroupId);
    }

    private navigateToNextRandomOptimalSubmission(): void {
        const randomInt = Math.floor(Math.random() * this.nextOptimalSubmissionIds.length);
        const url = getLinkToSubmissionAssessment(
            ExerciseType.MODELING,
            this.courseId,
            this.exerciseId,
            this.nextOptimalSubmissionIds[randomInt],
            this.examId,
            this.exerciseGroupId,
        );
        this.router.onSameUrlNavigation = 'reload';
        this.router.navigate(url);
    }

    private setPermissions() {
        if (this.exercise.course) {
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course!);
        } else {
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.exerciseGroup?.exam?.course!);
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
     */
    getAssessmentLink(submissionId: number): string[] {
        return getLinkToSubmissionAssessment(this.exercise.type!, this.courseId, this.exerciseId, submissionId, this.examId, this.exerciseGroupId);
    }
}
