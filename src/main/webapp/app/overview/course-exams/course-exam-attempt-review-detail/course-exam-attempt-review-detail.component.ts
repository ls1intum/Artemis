import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { faMagnifyingGlass, faCirclePlay } from '@fortawesome/free-solid-svg-icons';
import { StudentExam } from 'app/entities/student-exam.model';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/entities/exam.model';
import { Subscription, interval } from 'rxjs';

@Component({
    selector: 'jhi-course-exam-attempt-review-detail',
    templateUrl: './course-exam-attempt-review-detail.component.html',
    styleUrls: ['./course-exam-attempt-review-detail.component.scss'],
})
export class CourseExamAttemptReviewDetailComponent implements OnInit, OnDestroy {
    @Input() studentExam: StudentExam;
    // Both only needed for routing
    @Input() exam: Exam;
    @Input() courseId: number;
    // Index used to enumerate the attempts per student
    @Input() index: number;
    individualWorkingTime: number;
    studentExamState: Subscription;
    withinWorkingTime: boolean;

    // Icon
    faMagnifyingGlass = faMagnifyingGlass;
    faCirclePlay = faCirclePlay;

    constructor(private router: Router) {}

    /**
     * Calculate the individual working time for every submitted StudentExam. As the StudentExam need to be submitted, the
     * working time cannot change. For non-submitted StudentExams, the workingTimeLeftInSeconds() will be calculated dynamically
     * and therefore set to 0.
     */
    ngOnInit() {
        if (this.studentExam.started && this.studentExam.submitted && this.studentExam.startedDate && this.studentExam.submissionDate) {
            this.individualWorkingTime = dayjs(this.studentExam.submissionDate).diff(dayjs(this.studentExam.startedDate), 'seconds');
            this.withinWorkingTime = false;
        } else {
            this.individualWorkingTime = 0;
            // A subscription is used here to limit the number of calls
            this.studentExamState = interval(1000).subscribe(() => {
                this.isWithinWorkingTime();
                // If the StudentExam is not within the working time, the subscription can be unsubscribed, as the state will not change any more
                if (!this.withinWorkingTime) {
                    this.unsubscribeFromExamStateSubscription();
                }
            });
        }
    }

    ngOnDestroy() {
        this.unsubscribeFromExamStateSubscription();
    }

    unsubscribeFromExamStateSubscription() {
        if (this.studentExamState) {
            this.studentExamState.unsubscribe();
        }
    }

    /**
     * Determines if the given StudentExam is within the working time
     */
    isWithinWorkingTime() {
        if (this.studentExam.started && !this.studentExam.submitted && this.studentExam.startedDate && this.exam.workingTime) {
            const endDate = dayjs(this.studentExam.startedDate).add(this.exam.workingTime, 'seconds');
            this.withinWorkingTime = dayjs(endDate).isAfter(dayjs());
        }
    }

    /**
     * Dynamically calculates the remaining working time of an attempt, if the attempt is started, within the working time and not submitted
     */
    workingTimeLeftInSeconds(): number {
        if (this.studentExam.started && !this.studentExam.submitted && this.studentExam.startedDate && this.exam.workingTime) {
            return this.studentExam.startedDate.add(this.exam.workingTime, 'seconds').diff(dayjs(), 'seconds');
        }
        return 0;
    }

    /**
     * navigate to /courses/:courseId/exams/:examId/test-exam/:studentExamId
     * Used to open the corresponding studentExam
     */
    openStudentExam(): void {
        this.router.navigate(['courses', this.courseId, 'exams', this.exam.id, 'test-exam', this.studentExam.id]);
    }
}
