import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-control/working-time-control.component';
import dayjs from 'dayjs/esm';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Dialog } from 'primeng/dialog';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { combineLatest, takeWhile } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { StudentExamDetailTableRowComponent } from '../student-exam-detail-table-row/student-exam-detail-table-row.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

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
        Dialog,
    ],
})
export class StudentExamDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private studentExamService = inject(StudentExamService);
    private alertService = inject(AlertService);

    confirmToggleVisible = signal(false);

    examId = signal<number | undefined>(undefined);
    courseId = signal<number | undefined>(undefined);
    studentExam = signal<StudentExam | undefined>(undefined);
    achievedPointsPerExercise = signal<{ [exerciseId: number]: number } | undefined>(undefined);
    course = signal<Course | undefined>(undefined);
    student = signal<User | undefined>(undefined);
    isSavingWorkingTime = signal(false);
    isTestRun = signal(false);
    isTestExam = signal<boolean | undefined>(undefined);
    maxTotalPoints = signal(0);
    achievedTotalPoints = signal(0);
    bonusTotalPoints = signal(0);
    isSaving = signal(false);

    gradingScaleExists = signal(false);
    grade = signal<string | undefined>(undefined);
    gradeAfterBonus = signal<string | undefined>(undefined);
    isBonus = signal(false);
    passed = signal(false);

    // Icons
    faSave = faSave;

    workingTimeSeconds = signal(0);

    private componentActive = true;

    /**
     * Initialize the courseId and studentExam
     */
    ngOnInit(): void {
        combineLatest([this.route.data, this.route.params, this.route.url])
            .pipe(takeWhile(() => this.componentActive))
            .subscribe(([data, params, url]) => {
                this.examId.set(params.examId);
                this.courseId.set(params.courseId);
                const studentExamWithGrade = data.studentExam as StudentExamWithGradeDTO;
                this.setStudentExamWithGrade(studentExamWithGrade);
                this.isTestExam.set(studentExamWithGrade.studentExam?.exam?.testExam || false);
                this.isTestRun.set(url[1]?.toString() === 'test-runs');
            });
    }

    ngOnDestroy(): void {
        this.componentActive = false;
    }

    /**
     * Save the defined working time
     */
    saveWorkingTime() {
        this.isSavingWorkingTime.set(true);
        const studentExam = this.studentExam()!;
        this.studentExamService.updateWorkingTime(this.courseId()!, studentExam.exam!.id!, studentExam.id!, this.workingTimeSeconds()).subscribe({
            next: (res) => {
                if (res.body) {
                    this.setStudentExam(res.body);
                }
                this.isSavingWorkingTime.set(false);
                this.alertService.success('artemisApp.studentExamDetail.saveWorkingTimeSuccessful');
            },
            error: () => {
                this.alertService.error('artemisApp.studentExamDetail.workingTimeCouldNotBeSaved');
                this.isSavingWorkingTime.set(false);
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

        this.achievedPointsPerExercise.set(studentExamWithGrade.achievedPointsPerExercise);

        this.maxTotalPoints.set(studentExamWithGrade.maxPoints ?? 0);
        this.achievedTotalPoints.set(studentExamWithGrade.studentResult.overallPointsAchieved ?? 0);
        this.bonusTotalPoints.set(studentExamWithGrade.maxBonusPoints ?? 0);

        this.setExamGrade(studentExamWithGrade);
    }

    /**
     * Sets grade related information if a grading scale exists for the exam
     */
    setExamGrade(studentExamWithGrade: StudentExamWithGradeDTO) {
        if (studentExamWithGrade?.studentResult?.overallGrade != undefined) {
            this.gradingScaleExists.set(true);
            this.grade.set(studentExamWithGrade.studentResult.overallGrade);
            this.gradeAfterBonus.set(studentExamWithGrade.studentResult.gradeWithBonus?.finalGrade?.toString());
            this.passed.set(!!studentExamWithGrade.studentResult.hasPassed);
            this.isBonus.set(studentExamWithGrade.gradeType === GradeType.BONUS);
        }
    }

    /**
     * Sets the student exam, initialised the component which allows changing the working time and sets the score of the student.
     * @param studentExam
     */
    private setStudentExam(studentExam: StudentExam) {
        this.studentExam.set(studentExam);

        this.student.set(studentExam.user!);
        this.course.set(studentExam.exam!.course!);

        this.workingTimeSeconds.set(studentExam.workingTime ?? 0);

        studentExam.exercises?.forEach((exercise) => this.initExercise(exercise));
    }

    /**
     * Gets the correct explanation label for the grade depending on whether it is a bonus or it has bonus.
     */
    gradeExplanation = computed(() => {
        if (this.isBonus()) {
            return 'artemisApp.studentExams.bonus';
        } else if (this.gradeAfterBonus() != undefined) {
            return 'artemisApp.studentExams.gradeBeforeBonus';
        } else {
            return 'artemisApp.studentExams.grade';
        }
    });

    /**
     * Updates the points tallies based on the student’s results in the exercise.
     *
     * Also makes sure that the latest result is correctly connected to the student’s submission.
     * @param exercise which should be included in the total points calculations.
     */
    private initExercise(exercise: Exercise) {
        if (exercise.studentParticipations?.[0]?.submissions?.[0]) {
            setLatestSubmissionResult(exercise?.studentParticipations[0].submissions?.[0], getLatestSubmissionResult(exercise?.studentParticipations[0].submissions?.[0]));
        }
    }

    /**
     * Checks if the user should be able to edit the inputs.
     */
    isWorkingTimeFormDisabled = computed<boolean>(() => {
        const studentExam = this.studentExam();
        return this.isSavingWorkingTime() || (this.isTestRun() && !!studentExam?.submitted) || !studentExam?.exam;
    });

    individualEndDate = computed<dayjs.Dayjs | undefined>(() => {
        const studentExam = this.studentExam();
        if (!studentExam?.exam) {
            return undefined;
        }
        return dayjs(studentExam.exam.startDate).add(this.workingTimeSeconds(), 'seconds');
    });

    /**
     * Checks if the exam is over considering the individual working time of the student and the grace period
     */
    isExamOver = computed<boolean>(() => {
        const studentExam = this.studentExam();
        if (studentExam?.exam) {
            const individualExamEndDate = dayjs(studentExam.exam.startDate).add(studentExam.workingTime!, 'seconds').add(studentExam.exam.gracePeriod!, 'seconds');

            return individualExamEndDate.isBefore(dayjs());
        }

        return false;
    });

    /**
     * switch the 'submitted' state of the studentExam.
     */
    toggle() {
        this.isSaving.set(true);
        const studentExam = this.studentExam();
        if (studentExam?.exam && studentExam.exam.id) {
            this.studentExamService.toggleSubmittedState(this.courseId()!, studentExam.exam.id, studentExam.id!, studentExam.submitted!).subscribe({
                next: (res) => {
                    if (res.body) {
                        const updated = { ...studentExam, submissionDate: res.body.submissionDate, submitted: res.body.submitted };
                        this.studentExam.set(updated);
                    }
                    this.alertService.success('artemisApp.studentExamDetail.toggleSuccessful');
                    this.isSaving.set(false);
                },
                error: () => {
                    this.alertService.error('artemisApp.studentExamDetail.toggleFailed');
                    this.isSaving.set(false);
                },
            });
        }
    }

    /**
     * Open the confirmation dialog that requires the user's confirmation before toggling the submission state.
     */
    openConfirmationModal() {
        this.confirmToggleVisible.set(true);
    }

    confirmToggleSubmission() {
        this.confirmToggleVisible.set(false);
        this.toggle();
    }

    cancelToggleSubmission() {
        this.confirmToggleVisible.set(false);
    }
}
