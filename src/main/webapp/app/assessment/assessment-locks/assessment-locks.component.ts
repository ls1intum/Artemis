import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { DifferencePipe } from 'ngx-moment';
import { TranslateService } from '@ngx-translate/core';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';
import { Submission } from 'app/entities/submission.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { partition } from 'lodash';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { AlertService } from 'app/core/alert/alert.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingAssessmentDashboardComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-dashboard.component';
import { AssessmentType } from 'app/entities/assessment-type.model';
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
        private translateService: TranslateService,
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
        console.log('fileUploadSubmissions', this.fileUploadSubmissions.length);
        console.log('textSubmissions', this.textSubmissions.length);
        console.log('modelingSubmissions', this.modelingSubmissions.length);
    }

    getModelingSubmissions(exercise: ModelingExercise) {
        this.modelingSubmissionService.getModelingSubmissionsForExercise(exercise.id, { submittedOnly: true }).subscribe((response: HttpResponse<ModelingSubmission[]>) => {
            const tempSubmissions = response.body?.filter((submission) => submission.submitted);
            tempSubmissions?.forEach((submission) => {
                if (!submission.result || submission.result.assessor.id !== this.tutorId) {
                    return;
                }

                if (submission.result) {
                    // reconnect some associations
                    submission.result.submission = submission;
                    submission.result.participation = submission.participation;
                    submission.participation.results = [submission.result];
                }
                submission.exerciseId = exercise.id;

                this.modelingSubmissions.push(submission);
            });
        });
    }

    getTextSubmissions(exercise: TextExercise) {
        this.textSubmissionService.getTextSubmissionsForExercise(exercise.id, { submittedOnly: true }).subscribe((response: HttpResponse<TextSubmission[]>) => {
            const tempSubmissions = response.body?.filter((submission) => submission.submitted);
            tempSubmissions?.forEach((submission) => {
                if (!submission.result || submission.result.assessor.id !== this.tutorId) {
                    return;
                }

                if (submission.result) {
                    // reconnect some associations
                    submission.result.submission = submission;
                    submission.result.participation = submission.participation;
                    submission.participation.results = [submission.result];
                }
                submission.exerciseId = exercise.id;

                this.textSubmissions.push(submission);
            });
        });
    }

    getFileUploadSubmissions(exercise: FileUploadExercise) {
        this.fileUploadSubmissionService.getFileUploadSubmissionsForExercise(exercise.id, { submittedOnly: true }).subscribe((response: HttpResponse<FileUploadSubmission[]>) => {
            const tempSubmissions = response.body?.filter((submission) => submission.submitted);
            tempSubmissions?.forEach((submission) => {
                if (!submission.result || submission.result.assessor.id !== this.tutorId) {
                    return;
                }

                if (submission.result) {
                    // reconnect some associations
                    submission.result.submission = submission;
                    submission.result.participation = submission.participation;
                    submission.participation.results = [submission.result];
                }
                submission.exerciseId = exercise.id;

                this.fileUploadSubmissions.push(submission);
            });
        });
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
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
     */
    cancelFileUploadAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.fileUploadAssessmentsService.cancelAssessment(submission.id).subscribe(() => {
                this.getAllSubmissions(true);
            });
        }
    }

    getExerciseName(id: number) {
        const foundExercise = this.exercises.find((exercise) => exercise.id === id);
        return foundExercise?.title;
    }

    getLockCount() {
        this.getExercises();
        return this.modelingSubmissions.length + this.textSubmissions.length + this.fileUploadSubmissions.length;
    }

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
