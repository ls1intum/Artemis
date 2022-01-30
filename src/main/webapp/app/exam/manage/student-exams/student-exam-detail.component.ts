import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { User } from 'app/core/user/user.model';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { AlertService } from 'app/core/util/alert.service';
import { roundScoreSpecifiedByCourseSettings } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-student-exam-detail',
    templateUrl: './student-exam-detail.component.html',
    providers: [ArtemisDurationFromSecondsPipe],
})
export class StudentExamDetailComponent implements OnInit {
    courseId: number;
    studentExam: StudentExam;
    course: Course;
    student: User;
    workingTimeForm: FormGroup;
    isSavingWorkingTime = false;
    isTestRun = false;
    maxTotalPoints = 0;
    achievedTotalPoints = 0;
    bonusTotalPoints = 0;
    busy = false;

    examId: number;

    gradingScaleExists = false;
    grade?: string;
    isBonus = false;
    passed = false;

    // Icons
    faSave = faSave;

    constructor(
        private route: ActivatedRoute,
        private studentExamService: StudentExamService,
        private courseService: CourseManagementService,
        private artemisDurationFromSecondsPipe: ArtemisDurationFromSecondsPipe,
        private alertService: AlertService,
        private modalService: NgbModal,
        private gradingSystemService: GradingSystemService,
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
        this.route.data.subscribe(({ studentExam }) => this.setStudentExam(studentExam));

        this.courseService.find(this.courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
        });
        this.student = this.studentExam.user!;
        this.calculateGrade();
    }

    /**
     * Sets grade related information if a grading scale exists for the exam
     */
    calculateGrade() {
        const achievedPercentageScore = (this.achievedTotalPoints / this.maxTotalPoints) * 100;
        this.gradingSystemService.matchPercentageToGradeStepForExam(this.courseId, this.examId, achievedPercentageScore).subscribe((gradeObservable) => {
            if (gradeObservable && gradeObservable!.body) {
                this.gradingScaleExists = true;
                const gradeDTO = gradeObservable!.body;
                this.grade = gradeDTO.gradeName;
                this.passed = gradeDTO.isPassingGrade;
                this.isBonus = gradeDTO.gradeType === GradeType.BONUS;
            }
        });
    }

    /**
     * Save the defined working time
     */
    saveWorkingTime() {
        this.isSavingWorkingTime = true;
        const seconds = this.workingTimeForm.controls.hours.value * 3600 + this.workingTimeForm.controls.minutes.value * 60 + this.workingTimeForm.controls.seconds.value;
        this.studentExamService.updateWorkingTime(this.courseId, this.studentExam.exam!.id!, this.studentExam.id!, seconds).subscribe(
            (res) => {
                if (res.body) {
                    this.setStudentExam(res.body);
                }
                this.isSavingWorkingTime = false;
                this.alertService.success('artemisApp.studentExamDetail.saveWorkingTimeSuccessful');
            },
            () => {
                this.alertService.error('artemisApp.studentExamDetail.workingTimeCouldNotBeSaved');
                this.isSavingWorkingTime = false;
            },
        );
    }

    /**
     * Sets the student exam, initialised the component which allows changing the working time and sets the score of the student.
     * @param studentExam
     */
    private setStudentExam(studentExam: StudentExam) {
        this.studentExam = studentExam;
        this.initWorkingTimeForm();
        this.maxTotalPoints = 0;
        this.achievedTotalPoints = 0;
        this.bonusTotalPoints = 0;
        studentExam.exercises!.forEach((exercise) => {
            this.maxTotalPoints += exercise.maxPoints!;
            this.bonusTotalPoints += exercise.bonusPoints!;
            if (
                exercise.studentParticipations?.length &&
                exercise.studentParticipations.length > 0 &&
                exercise.studentParticipations[0].results?.length &&
                exercise.studentParticipations[0].results!.length > 0
            ) {
                if (exercise!.studentParticipations[0].submissions && exercise!.studentParticipations[0].submissions!.length > 0) {
                    exercise!.studentParticipations[0].submissions![0].results! = exercise.studentParticipations[0].results;
                    setLatestSubmissionResult(exercise?.studentParticipations[0].submissions?.[0], getLatestSubmissionResult(exercise?.studentParticipations[0].submissions?.[0]));
                }

                this.achievedTotalPoints += roundScoreSpecifiedByCourseSettings((exercise.studentParticipations[0].results[0].score! * exercise.maxPoints!) / 100, this.course);
            }
        });
    }

    private initWorkingTimeForm() {
        const workingTime = this.artemisDurationFromSecondsPipe.secondsToDuration(this.studentExam.workingTime!);
        this.workingTimeForm = new FormGroup({
            hours: new FormControl({ value: workingTime.days * 24 + workingTime.hours, disabled: this.examIsVisible() }, [Validators.min(0), Validators.required]),
            minutes: new FormControl({ value: workingTime.minutes, disabled: this.examIsVisible() }, [Validators.min(0), Validators.max(59), Validators.required]),
            seconds: new FormControl({ value: workingTime.seconds, disabled: this.examIsVisible() }, [Validators.min(0), Validators.max(59), Validators.required]),
        });
    }

    examIsVisible(): boolean {
        if (this.isTestRun) {
            // for test runs we always want to be able to change the working time
            return !!this.studentExam.submitted;
        } else if (this.studentExam.exam) {
            // Disable the form to edit the working time if the exam is already visible
            return dayjs(this.studentExam.exam.visibleDate).isBefore(dayjs());
        }
        // if exam is undefined, the form to edit the working time is disabled
        return true;
    }

    examIsOver(): boolean {
        if (this.studentExam.exam) {
            // only show the button when the exam is over
            return dayjs(this.studentExam.exam.endDate).add(this.studentExam.exam.gracePeriod!, 'seconds').isBefore(dayjs());
        }
        // if exam is undefined, we do not want to show the button
        return false;
    }

    getWorkingTimeToolTip(): string {
        return this.examIsVisible()
            ? 'You cannot change the individual working time after the exam has become visible.'
            : 'You can change the individual working time of the student here.';
    }

    /**
     * switch the 'submitted' state of the studentExam.
     */
    toggle() {
        this.busy = true;
        if (this.studentExam.exam && this.studentExam.exam.id) {
            this.studentExamService.toggleSubmittedState(this.courseId, this.studentExam.exam!.id!, this.studentExam.id!, this.studentExam!.submitted!).subscribe(
                (res) => {
                    if (res.body) {
                        this.studentExam.submissionDate = res.body.submissionDate;
                        this.studentExam.submitted = res.body.submitted;
                    }
                    this.alertService.success('artemisApp.studentExamDetail.toggleSuccessful');
                    this.busy = false;
                },
                () => {
                    this.alertService.error('artemisApp.studentExamDetail.togglefailed');
                    this.busy = false;
                },
            );
        }
    }

    /**
     * Open a modal that requires the user's confirmation.
     * @param content the modal content
     */
    openConfirmationModal(content: any) {
        this.modalService.open(content).result.then(
            (result: string) => {
                if (result === 'confirm') {
                    this.toggle();
                }
            },
            () => {},
        );
    }
}
