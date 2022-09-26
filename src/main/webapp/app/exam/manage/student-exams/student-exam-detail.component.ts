import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { AlertService } from 'app/core/util/alert.service';
import { round } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { getRelativeWorkingTimeExtension, normalWorkingTime } from 'app/exam/participate/exam.utils';
import { Exercise } from 'app/entities/exercise.model';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';

@Component({
    selector: 'jhi-student-exam-detail',
    templateUrl: './student-exam-detail.component.html',
    styleUrls: ['./student-exam-detail.component.scss'],
    providers: [ArtemisDurationFromSecondsPipe],
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
    busy = false;

    examId: number;

    gradingScaleExists = false;
    grade?: string;
    gradeAfterBonus?: string;
    isBonus = false;
    passed = false;

    workingTimeFormValues = {
        hours: 0,
        minutes: 0,
        seconds: 0,
        percent: 0,
    };

    // Icons
    faSave = faSave;

    constructor(
        private route: ActivatedRoute,
        private studentExamService: StudentExamService,
        private artemisDurationFromSecondsPipe: ArtemisDurationFromSecondsPipe,
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
        const seconds = this.getWorkingTimeSeconds();
        this.studentExamService.updateWorkingTime(this.courseId, this.studentExam.exam!.id!, this.studentExam.id!, seconds).subscribe({
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
        if (studentExamWithGrade.studentExam == undefined) {
            // This should not happen, the server endpoint should return studentExamWithGrade.studentExam.
            throw new Error('studentExamWithGrade.studentExam is undefined');
        }

        this.studentExam = studentExamWithGrade.studentExam;
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
     * @private
     */
    private initExercise(exercise: Exercise) {
        if (exercise.studentParticipations?.[0]?.submissions?.[0]) {
            exercise.studentParticipations[0].submissions[0].results = exercise.studentParticipations[0].results;
            setLatestSubmissionResult(exercise?.studentParticipations[0].submissions?.[0], getLatestSubmissionResult(exercise?.studentParticipations[0].submissions?.[0]));
        }
    }

    /**
     * Updates the form values based on the working time of the student exam.
     * @private
     */
    private initWorkingTimeForm() {
        this.setWorkingTimeDuration(this.studentExam.workingTime!);
        this.updateWorkingTimePercent();
    }

    /**
     * Updates the working time duration values of the form whenever the percent value has been changed by the user.
     */
    updateWorkingTimeDuration() {
        const regularWorkingTime = normalWorkingTime(this.studentExam.exam!)!;
        const seconds = round(regularWorkingTime * (1.0 + this.workingTimeFormValues.percent / 100), 0);
        this.setWorkingTimeDuration(seconds);
    }

    /**
     * Updates the hours, minutes, and seconds values of the form.
     * @param seconds the total number of seconds of working time.
     * @private
     */
    private setWorkingTimeDuration(seconds: number) {
        const workingTime = this.artemisDurationFromSecondsPipe.secondsToDuration(seconds);
        this.workingTimeFormValues.hours = workingTime.days * 24 + workingTime.hours;
        this.workingTimeFormValues.minutes = workingTime.minutes;
        this.workingTimeFormValues.seconds = workingTime.seconds;
    }

    /**
     * Uses the current durations saved in the form to update the extension percent value.
     */
    updateWorkingTimePercent() {
        this.workingTimeFormValues.percent = getRelativeWorkingTimeExtension(this.studentExam.exam!, this.getWorkingTimeSeconds());
    }

    /**
     * Calculates how many seconds the currently set working time has in total.
     * @private
     */
    private getWorkingTimeSeconds(): number {
        const duration = {
            days: 0,
            hours: this.workingTimeFormValues.hours,
            minutes: this.workingTimeFormValues.minutes,
            seconds: this.workingTimeFormValues.seconds,
        };
        return this.artemisDurationFromSecondsPipe.durationToSeconds(duration);
    }

    /**
     * Checks if the user should be able to edit the inputs.
     */
    isFormDisabled(): boolean {
        return this.isSavingWorkingTime || !this.canChangeExamWorkingTime();
    }

    /**
     * Checks if the working time of the exam can still be changed.
     * @private
     */
    private canChangeExamWorkingTime(): boolean {
        if (this.isTestRun) {
            // for unsubmitted test runs we always want to be able to change the working time
            return !this.studentExam.submitted;
        } else if (this.studentExam.exam) {
            // for student exams it can only be changed before the student is able to see it
            return dayjs().isBefore(dayjs(this.studentExam.exam.visibleDate));
        }

        // if there is no exam, then it cannot be changed
        return false;
    }

    examIsOver(): boolean {
        if (this.studentExam.exam) {
            // only show the button when the exam is over
            return dayjs(this.studentExam.exam.endDate).add(this.studentExam.exam.gracePeriod!, 'seconds').isBefore(dayjs());
        } else {
            return false;
        }
    }

    getWorkingTimeToolTip(): string {
        if (this.canChangeExamWorkingTime()) {
            return 'You can change the individual working time of the student here.';
        } else {
            return 'You cannot change the individual working time after the exam has become visible.';
        }
    }

    /**
     * switch the 'submitted' state of the studentExam.
     */
    toggle() {
        this.busy = true;
        if (this.studentExam.exam && this.studentExam.exam.id) {
            this.studentExamService.toggleSubmittedState(this.courseId, this.studentExam.exam.id, this.studentExam.id!, this.studentExam.submitted!).subscribe({
                next: (res) => {
                    if (res.body) {
                        this.studentExam.submissionDate = res.body.submissionDate;
                        this.studentExam.submitted = res.body.submitted;
                    }
                    this.alertService.success('artemisApp.studentExamDetail.toggleSuccessful');
                    this.busy = false;
                },
                error: () => {
                    this.alertService.error('artemisApp.studentExamDetail.togglefailed');
                    this.busy = false;
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
