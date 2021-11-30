import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { TranslateService } from '@ngx-translate/core';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { faBan, faFolderOpen } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-assessment-locks',
    templateUrl: './assessment-locks.component.html',
})
export class AssessmentLocksComponent implements OnInit {
    PROGRAMMING_EXERCISE = ExerciseType.PROGRAMMING;

    readonly ExerciseType = ExerciseType;

    course: Course;
    courseId: number;
    tutorId: number;
    examId?: number;
    showAll = false;
    exercises: Exercise[] = [];

    submissions: Submission[] = [];

    private cancelConfirmationText: string;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    // Icons
    faBan = faBan;
    faFolderOpen = faFolderOpen;

    constructor(
        private route: ActivatedRoute,
        private alertService: AlertService,
        private modelingAssessmentService: ModelingAssessmentService,
        private textAssessmentService: TextAssessmentService,
        private fileUploadAssessmentService: FileUploadAssessmentService,
        private programmingAssessmentService: ProgrammingAssessmentManualResultService,
        translateService: TranslateService,
        private location: Location,
        private courseService: CourseManagementService,
        private examManagementService: ExamManagementService,
    ) {
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    public async ngOnInit(): Promise<void> {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.examId = Number(params['examId']);
        });
        this.route.queryParams.subscribe((queryParams) => {
            this.tutorId = Number(queryParams['tutorId']);
        });
        this.getAllLockedSubmissions();
    }

    /**
     * Get all locked submissions for course and user.
     */
    getAllLockedSubmissions() {
        let lockedSubmissionsObservable;
        if (this.examId) {
            lockedSubmissionsObservable = this.examManagementService.findAllLockedSubmissionsOfExam(this.courseId, this.examId);
            this.showAll = true;
        } else {
            lockedSubmissionsObservable = this.courseService.findAllLockedSubmissionsOfCourse(this.courseId);
        }
        lockedSubmissionsObservable.subscribe(
            (response: HttpResponse<Submission[]>) => {
                this.submissions.push(...(response.body ?? []));
            },
            (response: string) => this.onError(response),
        );
    }

    /**
     * Cancel the current assessment.
     * @param canceledSubmission submission
     */
    cancelAssessment(canceledSubmission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            switch (canceledSubmission.submissionExerciseType) {
                case SubmissionExerciseType.MODELING:
                    this.modelingAssessmentService.cancelAssessment(canceledSubmission.id!).subscribe();
                    break;
                case SubmissionExerciseType.TEXT:
                    if (canceledSubmission.participation?.exercise?.id) {
                        this.textAssessmentService.cancelAssessment(canceledSubmission.participation.id!, canceledSubmission.id!).subscribe();
                    }
                    break;
                case SubmissionExerciseType.FILE_UPLOAD:
                    this.fileUploadAssessmentService.cancelAssessment(canceledSubmission.id!).subscribe();
                    break;
                case SubmissionExerciseType.PROGRAMMING:
                    this.programmingAssessmentService.cancelAssessment(canceledSubmission.id!).subscribe();
                    break;
                default:
                    break;
            }
            this.submissions = this.submissions.filter((submission) => submission !== canceledSubmission);
        }
    }

    /**
     * Pass on an error to the browser console and the alertService.
     * @param error
     */
    private onError(error: string) {
        this.alertService.error(error);
    }
}
