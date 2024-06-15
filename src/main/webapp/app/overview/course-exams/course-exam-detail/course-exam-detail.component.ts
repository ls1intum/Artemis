import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { faBook, faCalendarDay, faCirclePlay, faCircleStop, faMagnifyingGlass, faPenAlt, faPlay, faUserClock } from '@fortawesome/free-solid-svg-icons';
import { Subscription, interval } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';

// Enum to dynamically change the template-content
export const enum ExamState {
    // Case 1: StartDate is at least 10min prior
    UPCOMING = 'UPCOMING',
    // Case 2: StartDate is [10min, 0min) prior
    IMMINENT = 'IMMINENT',
    // Case 3: Exam is open for participation
    CONDUCTING = 'CONDUCTING',
    // Case 4: Exam is closed, but student(s) with time extension may still write the exam. For these students, the exam is not yet closed.
    TIMEEXTENSION = 'TIMEEXTENSION',
    // Case 5: Exam is closed
    CLOSED = 'CLOSED',
    // Case 6: Exam is open for review
    STUDENTREVIEW = 'STUDENTREVIEW',
    // Case 7: Fallback
    UNDEFINED = 'UNDEFINED',
    // Case 8: No more attempts
    NO_MORE_ATTEMPTS = 'NO_MORE_ATTEMPTS',
    // Case 9: Resume attempt (only for test exams)
    RESUME = 'RESUME',
}

@Component({
    selector: 'jhi-course-exam-detail',
    templateUrl: './course-exam-detail.component.html',
    styleUrls: ['./course-exam-detail.component.scss'],
})
export class CourseExamDetailComponent implements OnInit, OnDestroy {
    @Input() exam: Exam;
    @Input() course: Course;
    // Interims-boolean to limit the number of attempts for a test exam (currently max 1 attempt)
    @Input() maxAttemptsReached: boolean;
    examState: ExamState;
    examStateSubscription: Subscription;
    timeLeftToStart: number;

    studentExam?: StudentExam;

    // Icons
    faPenAlt = faPenAlt;
    faCirclePlay = faCirclePlay;
    faMagnifyingGlass = faMagnifyingGlass;
    faCalendarDay = faCalendarDay;
    faPlay = faPlay;
    faUserClock = faUserClock;
    faBook = faBook;
    faCircleStop = faCircleStop;

    constructor(
        private router: Router,
        private examParticipationService: ExamParticipationService,
    ) {}

    ngOnInit() {
        // A subscription is used here to limit the number of calls
        this.examStateSubscription = interval(1000).subscribe(() => {
            this.updateExamState();
        });
    }

    ngOnDestroy() {
        this.cancelExamStateSubscription();
    }

    cancelExamStateSubscription() {
        this.examStateSubscription?.unsubscribe();
    }

    /**
     * navigate to /courses/:courseId/exams/:examId for real exams or
     * /courses/:courseId/exams/:examId/test-exam/start for test exams
     */
    openExam() {
        if (this.exam.testExam) {
            if (this.examState === ExamState.NO_MORE_ATTEMPTS || this.examState === ExamState.CLOSED) {
                return;
            }
            this.router.navigate(['courses', this.course.id, 'exams', this.exam.id, 'test-exam', 'start']);
        } else {
            this.router.navigate(['courses', this.course.id, 'exams', this.exam.id]);
        }
        // TODO: store the (plain) selected exam in the some service so that it can be obtained on other pages
        // also make sure that the exam objects does not contain the course and all exercises
    }

    /**
     * Updates the status of the exam every second. The cases are explained at the ExamState enum.
     */
    updateExamState() {
        if (this.maxAttemptsReached) {
            this.examState = ExamState.NO_MORE_ATTEMPTS;
            this.cancelExamStateSubscription();
            return;
        }
        if (this.exam.startDate && dayjs().isBefore(this.exam.startDate)) {
            if (dayjs(this.exam.startDate).diff(dayjs(), 'seconds') < 600) {
                this.examState = ExamState.IMMINENT;
            } else {
                this.examState = ExamState.UPCOMING;
            }
            this.timeLeftToStartInSeconds();
            return;
        }
        if (!this.exam.testExam && this.exam.endDate && dayjs().isBefore(this.exam.endDate)) {
            this.examState = ExamState.CONDUCTING;
            return;
        }

        this.updateExamStateWithStudentExamOrTestExam();
    }

    updateExamStateWithStudentExamOrTestExam() {
        if (!this.studentExam && this.course?.id && this.exam?.id) {
            this.examParticipationService
                .getOwnStudentExam(this.course.id, this.exam.id)
                .subscribe({
                    next: (studentExam) => {
                        this.studentExam = studentExam;
                    },
                })
                .add(() => this.updateExamStateWithLoadedStudentExamOrTestExam());
        } else {
            this.updateExamStateWithLoadedStudentExamOrTestExam();
        }
    }

    updateExamStateWithLoadedStudentExamOrTestExam() {
        const potentialLaterEndDate = this.studentExam?.workingTime ? dayjs(this.exam.startDate).add(this.studentExam.workingTime, 'seconds') : undefined;
        const noOrOverPotentialLaterEndDate = !potentialLaterEndDate || dayjs().isAfter(potentialLaterEndDate);
        if ((!this.studentExam || (!this.studentExam.submitted && noOrOverPotentialLaterEndDate)) && !this.exam.testExam) {
            // Normal exam is over and student did not participate (no student exam was loaded nor submitted). We can cancel the subscription and the exam is closed
            this.examState = ExamState.CLOSED;
            this.cancelExamStateSubscription();
            return;
        }

        if (this.exam.examStudentReviewStart && this.exam.examStudentReviewEnd && dayjs().isBetween(this.exam.examStudentReviewStart, this.exam.examStudentReviewEnd)) {
            this.examState = ExamState.STUDENTREVIEW;
            return;
        }
        if (dayjs().isAfter(this.exam.endDate)) {
            if (!this.exam.testExam) {
                // The longest individual working time is stored on the server side, but should not be extra loaded. Therefore, a sufficiently large time extension is selected.
                const endDateWithTimeExtension = dayjs(this.exam.endDate).add(this.exam.workingTime! * 3, 'seconds');
                if (dayjs().isBefore(endDateWithTimeExtension)) {
                    this.examState = ExamState.TIMEEXTENSION;
                    return;
                } else {
                    this.examState = ExamState.CLOSED;
                    return;
                }
            } else {
                this.examState = ExamState.CLOSED;
                // For test exams, we can cancel the subscription and lock the possibility to click on the exam tile
                // For real exams, a CLOSED real exam can switch into a STUDENTREVIEW real exam
                this.maxAttemptsReached = true;
                this.cancelExamStateSubscription();
                return;
            }
        } else {
            if (this.isWithinWorkingTime()) {
                this.examState = ExamState.RESUME;
                return;
            } else if (this.exam.endDate && dayjs().isBefore(this.exam.endDate)) {
                this.examState = ExamState.CONDUCTING;
                return;
            }
        }
        this.examState = ExamState.UNDEFINED;
        this.cancelExamStateSubscription();
    }

    /**
     * Dynamically calculates the time left until the exam start
     */
    timeLeftToStartInSeconds() {
        this.timeLeftToStart = dayjs(this.exam.startDate!).diff(dayjs(), 'seconds');
    }

    /**
     * Determines if the given StudentExam is (still) within the working time
     */
    isWithinWorkingTime() {
        if (this.studentExam?.started && !this.studentExam.submitted && this.studentExam.startedDate && this.exam.workingTime) {
            const endDate = dayjs(this.studentExam.startedDate).add(this.exam.workingTime, 'seconds');
            return dayjs(endDate).isAfter(dayjs());
        }
    }
}
