import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { AccountService } from 'app/core/auth/account.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { TranslateService } from '@ngx-translate/core';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/alert/alert.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';

@Component({
    selector: 'jhi-assessment-locks',
    templateUrl: './assessment-locks.component.html',
})
export class AssessmentLocksComponent implements OnInit {
    course: Course;
    courseId: number;
    tutorId: number;
    exercises: Exercise[] = [];

    submissions: Submission[] = [];

    private cancelConfirmationText: string;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    constructor(
        private route: ActivatedRoute,
        private accountService: AccountService,
        private jhiAlertService: AlertService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private textSubmissionService: TextSubmissionService,
        private textAssessmentsService: TextAssessmentsService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private fileUploadAssessmentsService: FileUploadAssessmentsService,
        translateService: TranslateService,
        private location: Location,
        private courseService: CourseManagementService,
    ) {
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    public async ngOnInit(): Promise<void> {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });
        this.route.queryParams.subscribe((queryParams) => {
            this.tutorId = Number(queryParams['tutorId']);
        });
        this.getExercises();
    }

    /**
     * Get exercises for course
     */
    getExercises() {
        this.courseService.getForTutors(this.courseId).subscribe(
            (res: HttpResponse<Course>) => {
                if (!res.body) {
                    return;
                }

                this.course = res.body;
                this.course.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course);
                this.course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);

                if (this.course.exercises.length > 0) {
                    this.exercises = this.course.exercises;
                }

                this.getAllSubmissions();
            },
            (response: string) => this.onError(response),
        );
    }

    /**
     * Get submissions for exercise types modeling, text and file upload.
     */
    getAllSubmissions() {
        this.courseService
            .findAllSubmissionsOfCourse(this.courseId)
            .map((response: HttpResponse<Submission[]>) =>
                response.body
                    ?.filter((submission) => submission.result && submission.submitted && submission.result.assessor.id === this.tutorId)
                    .map((submission: Submission) => {
                        if (submission.result) {
                            // reconnect some associations
                            submission.result.submission = submission;
                            submission.result.participation = submission.participation;
                            submission.participation.results = [submission.result];
                            // submission.participation.exercise = exercise;
                        }

                        return submission;
                    }),
            )
            .subscribe((loadedSubmissions: Submission[]) => {
                this.submissions.push(...loadedSubmissions);
            });
    }

    /**
     * Cancel the current assessment.
     * @param canceledSubmission submission
     */
    cancelAssessment(canceledSubmission: Submission) {
        console.log(canceledSubmission);
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            switch (canceledSubmission.submissionExerciseType) {
                case SubmissionExerciseType.MODELING:
                    this.modelingAssessmentService.cancelAssessment(canceledSubmission.id).subscribe();
                    break;
                case SubmissionExerciseType.TEXT:
                    if (canceledSubmission.participation.exercise?.id !== undefined) {
                        this.textAssessmentsService.cancelAssessment(canceledSubmission.participation.exercise.id, canceledSubmission.id).subscribe();
                    }
                    break;
                case SubmissionExerciseType.FILE_UPLOAD:
                    this.fileUploadAssessmentsService.cancelAssessment(canceledSubmission.id).subscribe();
                    break;
                default:
                    break;
            }
            this.submissions = this.submissions.filter((submission) => submission !== canceledSubmission);
        }
    }

    /**
     * Navigates back in browser.
     */
    back() {
        this.location.back();
    }

    /**
     * Pass on an error to the browser console and the jhiAlertService.
     * @param error
     */
    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
