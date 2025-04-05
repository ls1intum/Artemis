import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Course } from 'app/core/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-control/working-time-control.component';
import dayjs from 'dayjs/esm';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentExamWithGradeDTO } from 'app/exam/shared/entities/exam-score-dtos.model';
import { combineLatest, takeWhile } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { StudentExamDetailTableRowComponent } from './student-exam-detail-table-row/student-exam-detail-table-row.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-student-exam-detail',
    templateUrl: './student-exam-detail.component.html',
    styleUrls: ['./student-exam-detail.component.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        WorkingTimeControlComponent,
        FaIconComponent,
        TestExamWorkingTimeComponent,
        NgbTooltip,
        RouterLink,
        StudentExamDetailTableRowComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class StudentExamDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private studentExamService = inject(StudentExamService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    examId: number;
    courseId: number;
    studentExam: StudentExam;
    achievedPointsPerExercise: { [exerciseId: number]: number };
    course: Course;
    student: User;
    isSavingWorkingTime = false;
    isTestRun = false;
    isTestExam: boolean;
    maxTotalPoints = 0;
    achievedTotalPoints = 0;
    bonusTotalPoints = 0;
    isSaving = false;

    gradingScaleExists = false;
    grade?: string;
    gradeAfterBonus?: string;
    isBonus = false;
    passed = false;

    // Icons
    faSave = faSave;

    workingTimeSeconds = 0;

    private componentActive = true;

    /**
     * Initialize the courseId and studentExam
     */
    ngOnInit(): void {
        combineLatest([this.route.data, this.route.params, this.route.url])
            .pipe(takeWhile(() => this.componentActive))
            .subscribe(([data, params, url]) => {
                this.examId = params.examId;
                this.courseId = params.courseId;
                const studentExamWithGrade = data.studentExam as StudentExamWithGradeDTO;
                this.setStudentExamWithGrade(studentExamWithGrade);
                this.isTestExam = studentExamWithGrade.studentExam?.exam?.testExam || false;
                this.isTestRun = url[1]?.toString() === 'test-runs';
            });
    }

    ngOnDestroy(): void {
        this.componentActive = false;
    }

    /**
     * Save the defined working time
     */
    saveWorkingTime() {
        this.isSavingWorkingTime = true;
        this.studentExamService.updateWorkingTime(this.courseId, this.studentExam.exam!.id!, this.studentExam.id!, this.workingTimeSeconds).subscribe({
            next: (res) => {
                if (res.body) {
                    this.setStudentExam(res.body);
                }
                this.isSavingWorkingTime = false;
                this.alertService.success('artemisApp.studentExamDetail.saveWorkingTimeSuccessful');
            },
            error: () => {
                this.alertService.error('artemisApp.studentExamDetail.workingTimeCouldNotBeSaved');
                this.isSavingWorkingTime = false;
            },
        });
    }

    /**
     * Sets the student exam, initialised the component which allows changing the working time and sets the score of the student.
     * @param studentExamWithGrade
     */
    private setStudentExamWithGrade(studentExamWithGrade: StudentExamWithGradeDTO) {
        if (!studentExamWithGrade.studentExam) {
            // This should not happen, the server endpoint should return studentExamWithGrade.studentExam.
            throw new Error('studentExamWithGrade.studentExam is undefined');
        }

        const studentExam = studentExamWithGrade.studentExam;

        if (studentExam.examSessions) {
            // show the oldest session first (sessions are created sequentially so we can sort after id)
            studentExam.examSessions.sort((s1, s2) => s1.id! - s2.id!);
        }

        this.setStudentExam(studentExam);

        this.achievedPointsPerExercise = studentExamWithGrade.achievedPointsPerExercise;

        this.maxTotalPoints = studentExamWithGrade.maxPoints ?? 0;
        this.achievedTotalPoints = studentExamWithGrade.studentResult.overallPointsAchieved ?? 0;
        this.bonusTotalPoints = studentExamWithGrade.maxBonusPoints ?? 0;

        this.setExamGrade(studentExamWithGrade);
    }

    /**
     * Sets grade related information if a grading scale exists for the exam
     */
    setExamGrade(studentExamWithGrade: StudentExamWithGradeDTO) {
        if (studentExamWithGrade?.studentResult?.overallGrade != undefined) {
            this.gradingScaleExists = true;
            this.grade = studentExamWithGrade.studentResult.overallGrade;
            this.gradeAfterBonus = studentExamWithGrade.studentResult.gradeWithBonus?.finalGrade?.toString();
            this.passed = !!studentExamWithGrade.studentResult.hasPassed;
            this.isBonus = studentExamWithGrade.gradeType === GradeType.BONUS;
        }
    }

    /**
     * Sets the student exam, initialised the component which allows changing the working time and sets the score of the student.
     * @param studentExam
     */
    private setStudentExam(studentExam: StudentExam) {
        this.studentExam = studentExam;

        this.student = studentExam.user!;
        this.course = studentExam.exam!.course!;

        this.workingTimeSeconds = studentExam.workingTime ?? 0;

        studentExam.exercises?.forEach((exercise) => this.initExercise(exercise));
    }

    /**
     * Gets the correct explanation label for the grade depending on whether it is a bonus or it has bonus.
     */
    get gradeExplanation() {
        if (this.isBonus) {
            return 'artemisApp.studentExams.bonus';
        } else if (this.gradeAfterBonus != undefined) {
            return 'artemisApp.studentExams.gradeBeforeBonus';
        } else {
            return 'artemisApp.studentExams.grade';
        }
    }

    /**
     * Updates the points tallies based on the student’s results in the exercise.
     *
     * Also makes sure that the latest result is correctly connected to the student’s submission.
     * @param exercise which should be included in the total points calculations.
     */
    private initExercise(exercise: Exercise) {
        if (exercise.studentParticipations?.[0]?.submissions?.[0]) {
            exercise.studentParticipations[0].submissions[0].results = exercise.studentParticipations[0].results;
            setLatestSubmissionResult(exercise?.studentParticipations[0].submissions?.[0], getLatestSubmissionResult(exercise?.studentParticipations[0].submissions?.[0]));
        }
    }

    /**
     * Checks if the user should be able to edit the inputs.
     */
    get isWorkingTimeFormDisabled(): boolean {
        return this.isSavingWorkingTime || (this.isTestRun && !!this.studentExam.submitted) || !this.studentExam.exam;
    }

    get individualEndDate(): dayjs.Dayjs | undefined {
        return dayjs(this.studentExam.exam!.startDate).add(this.workingTimeSeconds, 'seconds');
    }

    /**
     * Checks if the exam is over considering the individual working time of the student and the grace period
     */
    get isExamOver(): boolean {
        if (this.studentExam.exam) {
            const individualExamEndDate = dayjs(this.studentExam.exam.startDate).add(this.studentExam.workingTime!, 'seconds').add(this.studentExam.exam.gracePeriod!, 'seconds');

            return individualExamEndDate.isBefore(dayjs());
        }

        return false;
    }

    /**
     * switch the 'submitted' state of the studentExam.
     */
    toggle() {
        this.isSaving = true;
        if (this.studentExam.exam && this.studentExam.exam.id) {
            this.studentExamService.toggleSubmittedState(this.courseId, this.studentExam.exam.id, this.studentExam.id!, this.studentExam.submitted!).subscribe({
                next: (res) => {
                    if (res.body) {
                        this.studentExam.submissionDate = res.body.submissionDate;
                        this.studentExam.submitted = res.body.submitted;
                    }
                    this.alertService.success('artemisApp.studentExamDetail.toggleSuccessful');
                    this.isSaving = false;
                },
                error: () => {
                    this.alertService.error('artemisApp.studentExamDetail.toggleFailed');
                    this.isSaving = false;
                },
            });
        }
    }

    /**
     * Open a modal that requires the user's confirmation.
     * @param content the modal content
     */
    openConfirmationModal(content: any) {
        this.modalService.open(content).result.then((result: string) => {
            if (result === 'confirm') {
                this.toggle();
            }
        });
    }
}
