import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { TranslateService } from '@ngx-translate/core';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/core/shared/entities/course.model';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/shared/service/alert.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/text/manage/assess/text-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { faBan, faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { combineLatest } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-assessment-locks',
    templateUrl: './assessment-locks.component.html',
    imports: [TranslateDirective, FaIconComponent, NgbTooltip, RouterLink, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class AssessmentLocksComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private textAssessmentService = inject(TextAssessmentService);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);
    private programmingAssessmentService = inject(ProgrammingAssessmentManualResultService);
    private courseService = inject(CourseManagementService);
    private examManagementService = inject(ExamManagementService);

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

    constructor() {
        const translateService = inject(TranslateService);
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    public ngOnInit() {
        combineLatest([this.route.params, this.route.queryParams]).subscribe(([params, queryParams]) => {
            this.courseId = Number(params['courseId']);
            this.examId = Number(params['examId']);
            this.tutorId = Number(queryParams['tutorId']);

            this.getAllLockedSubmissions();
        });
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
        lockedSubmissionsObservable.subscribe({
            next: (response: HttpResponse<Submission[]>) => {
                this.submissions.push(...(response.body ?? []));
            },
            error: (response: string) => this.onError(response),
        });
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
