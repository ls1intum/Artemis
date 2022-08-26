import { Component, OnDestroy, OnInit } from '@angular/core';
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
import { getLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SortService } from 'app/shared/service/sort.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faEdit, faBan, faFolderOpen, faSort } from '@fortawesome/free-solid-svg-icons';
import { AbstractAssessmentDashboard } from 'app/exercises/shared/dashboards/tutor/abstract-assessment-dashboard';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './modeling-assessment-dashboard.component.html',
    providers: [],
})
export class ModelingAssessmentDashboardComponent extends AbstractAssessmentDashboard implements OnInit, OnDestroy {
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
    busy: boolean;
    userId: number;
    canOverrideAssessments: boolean;

    // Icons
    faSort = faSort;
    faFolderOpen = faFolderOpen;
    faBan = faBan;
    faEdit = faEdit;

    constructor(
        private route: ActivatedRoute,
        private alertService: AlertService,
        private router: Router,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private resultService: ResultService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private modalService: NgbModal,
        private eventManager: EventManager,
        private accountService: AccountService,
        private translateService: TranslateService,
        private sortService: SortService,
    ) {
        super();
        this.reverse = false;
        this.predicate = 'id';
        this.submissions = [];
        this.filteredSubmissions = [];
        this.canOverrideAssessments = this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR]);
        translateService.get('artemisApp.modelingAssessmentEditor.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
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
            this.route.queryParams.subscribe((queryParams) => {
                if (queryParams['filterOption']) {
                    this.filterOption = Number(queryParams['filterOption']);
                }
            });
            this.exerciseId = params['exerciseId'];
            this.exerciseService.find(this.exerciseId).subscribe((res: HttpResponse<Exercise>) => {
                if (res.body!.type === ExerciseType.MODELING) {
                    this.exercise = res.body as ModelingExercise;
                    this.courseId = this.exercise.course ? this.exercise.course.id! : this.exercise.exerciseGroup!.exam!.course!.id!;
                    this.getSubmissions();
                    this.numberOfCorrectionrounds = this.exercise.exerciseGroup ? this.exercise!.exerciseGroup.exam!.numberOfCorrectionRoundsInExam! : 1;
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
                this.applyChartFilter(this.submissions);
            });
    }

    getAssessmentRouterLink(participationId: number, submissionId: number): string[] {
        return getLinkToSubmissionAssessment(ExerciseType.MODELING, this.courseId, this.exerciseId, participationId, submissionId, this.examId, this.exerciseGroupId);
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
