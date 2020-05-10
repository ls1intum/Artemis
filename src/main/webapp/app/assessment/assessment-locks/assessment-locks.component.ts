import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { TranslateService } from '@ngx-translate/core';
import { Submission } from 'app/entities/submission.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/alert/alert.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextExercise } from 'app/entities/text-exercise.model';
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

    modelingSubmissions: ModelingSubmission[] = [];
    textSubmissions: TextSubmission[] = [];
    fileUploadSubmissions: FileUploadSubmission[] = [];

    private cancelConfirmationText: string;

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
     * @param reload tells whether to reload all submissions
     */
    getAllSubmissions(reload?: boolean) {
        if (reload) {
            this.modelingSubmissions = [];
            this.textSubmissions = [];
            this.fileUploadSubmissions = [];
        }
        for (const exercise of this.exercises) {
            switch (exercise.type) {
                case ExerciseType.MODELING:
                    this.getModelingSubmissions(exercise as ModelingExercise);
                    break;

                case ExerciseType.TEXT:
                    this.getTextSubmissions(exercise as TextExercise);
                    break;

                case ExerciseType.FILE_UPLOAD:
                    this.getFileUploadSubmissions(exercise as FileUploadExercise);
                    break;

                default:
                    break;
            }
        }
    }

    /**
     * Get submissions for modeling exercise
     * @param exercise modeling exercise
     */
    getModelingSubmissions(exercise: ModelingExercise) {
        this.modelingSubmissionService.getModelingSubmissionsForExercise(exercise.id, { submittedOnly: true }).subscribe((response: HttpResponse<ModelingSubmission[]>) => {
            return response.body?.forEach((submission) => {
                if (!submission.result || !submission.submitted || submission.result.assessor.id !== this.tutorId) {
                    return;
                }
                // reconnect some associations
                submission.result.submission = submission;
                submission.result.participation = submission.participation;
                submission.participation.results = [submission.result];
                submission.exerciseId = exercise.id;

                this.modelingSubmissions.push(submission);
            });
        });
    }

    /**
     * Get submissions for text exercise
     * @param exercise text exercise
     */
    getTextSubmissions(exercise: TextExercise) {
        this.textSubmissionService.getTextSubmissionsForExercise(exercise.id, { submittedOnly: true }).subscribe((response: HttpResponse<TextSubmission[]>) => {
            return response.body?.forEach((submission) => {
                if (!submission.result || !submission.submitted || submission.result.assessor.id !== this.tutorId) {
                    return;
                }
                // reconnect some associations
                submission.result.submission = submission;
                submission.result.participation = submission.participation;
                submission.participation.results = [submission.result];
                submission.exerciseId = exercise.id;

                this.textSubmissions.push(submission);
            });
        });
    }

    /**
     * Get submissions for file uplaod exercise
     * @param exercise file upload exercise
     */
    getFileUploadSubmissions(exercise: FileUploadExercise) {
        this.fileUploadSubmissionService.getFileUploadSubmissionsForExercise(exercise.id, { submittedOnly: true }).subscribe((response: HttpResponse<FileUploadSubmission[]>) => {
            return response.body?.forEach((submission) => {
                if (!submission.result || !submission.submitted || submission.result.assessor.id !== this.tutorId) {
                    return;
                }
                // reconnect some associations
                submission.result.submission = submission;
                submission.result.participation = submission.participation;
                submission.participation.results = [submission.result];
                submission.exerciseId = exercise.id;

                this.fileUploadSubmissions.push(submission);
            });
        });
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     * @param submission submission
     */
    cancelModelingAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.modelingAssessmentService.cancelAssessment(submission.id).subscribe(() => {
                this.getAllSubmissions(true);
            });
        }
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     * @param submission submission
     */
    cancelTextAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel && submission.exerciseId) {
            this.textAssessmentsService.cancelAssessment(submission.exerciseId, submission.id).subscribe(() => {
                this.getAllSubmissions(true);
            });
        }
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     * @param submission submission
     */
    cancelFileUploadAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.fileUploadAssessmentsService.cancelAssessment(submission.id).subscribe(() => {
                this.getAllSubmissions(true);
            });
        }
    }

    /**
     * Get the name of an exercise by id.
     * @param id id of exercise
     */
    getExerciseName(id: number) {
        const foundExercise = this.exercises.find((exercise) => exercise.id === id);
        return foundExercise?.title;
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
