import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';

@Component({
    selector: 'jhi-student-exam-detail',
    templateUrl: './student-exam-detail.component.html',
    styleUrls: ['./student-exam-detail.component.scss'],
})
export class StudentExamDetailComponent implements OnInit {
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

    examId: number;

    gradingScaleExists = false;
    grade?: string;
    gradeAfterBonus?: string;
    isBonus = false;
    passed = false;

    // Icons
    faSave = faSave;

    workingTimeSeconds = 0;
    lastSavedWorkingTimeSeconds = 0;

    constructor(
        private route: ActivatedRoute,
        private studentExamService: StudentExamService,
        private alertService: AlertService,
        private modalService: NgbModal,
    ) {}

    /**
     * Initialize the courseId and studentExam
     */
    ngOnInit(): void {
        this.isTestRun = this.route.snapshot.url[1]?.toString() === 'test-runs';
        this.loadStudentExam();
    }

    /**
     * Load the course and the student exam
     */
    loadStudentExam() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.route.data.subscribe(({ studentExam }) => this.setStudentExamWithGrade(studentExam));
        this.isTestExam = this.studentExam.exam!.testExam!;
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

        this.studentExam = studentExamWithGrade.studentExam;
        if (this.studentExam.examSessions) {
            // show the oldest session first (sessions are created sequentially so we can sort after id)
            this.studentExam.examSessions.sort((s1, s2) => s1.id! - s2.id!);
        }
        this.achievedPointsPerExercise = studentExamWithGrade.achievedPointsPerExercise;

        this.initWorkingTimeForm();

        this.maxTotalPoints = studentExamWithGrade.maxPoints ?? 0;
        this.achievedTotalPoints = studentExamWithGrade.studentResult.overallPointsAchieved ?? 0;
        this.bonusTotalPoints = studentExamWithGrade.maxBonusPoints ?? 0;

        this.student = studentExamWithGrade.studentExam.user!;
        this.course = studentExamWithGrade.studentExam.exam!.course!;

        studentExamWithGrade.studentExam.exercises!.forEach((exercise) => this.initExercise(exercise));
        this.setExamGrade(studentExamWithGrade);
    }

    /**
     * Sets the student exam, initialised the component which allows changing the working time and sets the score of the student.
     * @param studentExam
     */
    private setStudentExam(studentExam: StudentExam) {
        this.studentExam = studentExam;

        this.initWorkingTimeForm();

        this.student = this.studentExam.user!;
        this.course = this.studentExam.exam!.course!;

        studentExam.exercises!.forEach((exercise) => this.initExercise(exercise));
    }

    /**
     * Gets the correct explanation label for the grade depending on whether it is a bonus or it has bonus.
     */
    getGradeExplanation() {
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

    private initWorkingTimeForm() {
        this.workingTimeSeconds = this.studentExam.workingTime ?? 0;
        this.lastSavedWorkingTimeSeconds = this.studentExam.workingTime ?? 0;
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
    isExamOver(): boolean {
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
                    this.alertService.error('artemisApp.studentExamDetail.togglefailed');
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
