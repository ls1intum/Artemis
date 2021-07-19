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
    courseId: number;
    examId: number;
    exerciseId: number;
    exerciseGroupId: number;
    numberOfCorrectionrounds = 1;

    private cancelConfirmationText: string;

    // all available submissions
    submissions: ModelingSubmission[];
    filteredSubmissions: ModelingSubmission[];

    eventSubscriber: Subscription;
    assessedSubmissions: number;
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
                    this.getSubmissions();
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
        this.eventSubscriber = this.eventManager.subscribe('resultListModification', () => this.getSubmissions());
    }

    /**
     * Get all results for the current modeling exercise, this includes information about all submitted models ( = submissions)
     *
     */
    getSubmissions() {
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
                this.assessedSubmissions = this.submissions.filter((submission) => {
                    const result = getLatestSubmissionResult(submission);
                    setLatestSubmissionResult(submission, result);
                    return !!result;
                }).length;
            });
    }

    updateFilteredSubmissions(filteredSubmissions: Submission[]) {
        this.filteredSubmissions = filteredSubmissions as ModelingSubmission[];
    }

    getAssessmentRouterLink(participationId: number, submissionId: number): string[] {
        return getLinkToSubmissionAssessment(ExerciseType.MODELING, this.courseId, this.exerciseId, participationId, submissionId, this.examId, this.exerciseGroupId);
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
                this.getSubmissions();
            });
        }
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    public sortRows() {
        this.sortService.sortByProperty(this.filteredSubmissions, this.predicate, this.reverse);
    }

    /**
     * get the link for the assessment of a specific submission of the current exercise
     */
    getAssessmentLink(participationId: number, submissionId: number): string[] {
        return getLinkToSubmissionAssessment(this.exercise.type!, this.courseId, this.exerciseId, participationId, submissionId, this.examId, this.exerciseGroupId);
    }
}
