import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { faCirclePlay, faFileCircleXmark, faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
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
    // Both needed for routing (and for the exam.workingTime)
    @Input() exam: Exam;
    @Input() courseId: number;
    // Index used to enumerate the attempts per student
    @Input() index: number;
    @Input() latestExam: boolean;
    studentExamState: Subscription;

    // Helper-Variables
    withinWorkingTime: boolean;

    // Icons
    faMagnifyingGlass = faMagnifyingGlass;
    faCirclePlay = faCirclePlay;
    faFileCircleXmark = faFileCircleXmark;

    constructor(private router: Router) {}

    /**
     * Calculate the individual working time for every submitted StudentExam. As the StudentExam needs to be submitted, the
     * working time cannot change.
     * For the latest StudentExam, which is still within the allowed working time, a subscription is used to periodically check this.
     */
    ngOnInit() {
        if (this.studentExam.started && this.studentExam.submitted && this.studentExam.startedDate && this.studentExam.submissionDate) {
            this.withinWorkingTime = false;
        } else if (this.latestExam) {
            // A subscription is used here to limit the number of calls for the countdown of the remaining workingTime.
            this.studentExamState = interval(1000).subscribe(() => {
                this.isWithinWorkingTime();
                // If the StudentExam is no longer within the working time, the subscription can be unsubscribed, as the state will not change anymore
                if (!this.withinWorkingTime) {
                    this.unsubscribeFromExamStateSubscription();
                }
            });
        } else {
            this.withinWorkingTime = false;
        }
    }

    ngOnDestroy() {
        this.unsubscribeFromExamStateSubscription();
    }

    /**
     * Used to unsubscribe from the studentExamState Subscriptions
     */
    unsubscribeFromExamStateSubscription() {
        this.studentExamState?.unsubscribe();
    }

    /**
     * Determines if the given StudentExam is (still) within the working time
     */
    isWithinWorkingTime() {
        if (this.studentExam.started && !this.studentExam.submitted && this.studentExam.startedDate && this.exam.workingTime) {
            const endDate = dayjs(this.studentExam.startedDate).add(this.exam.workingTime, 'seconds');
            this.withinWorkingTime = dayjs(endDate).isAfter(dayjs());
        }
    }

    /**
     * Dynamically calculates the remaining working time of an attempt, if the attempt is started, within the working time and not yet submitted
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
        if (this.studentExam.submitted || this.withinWorkingTime) {
            this.router.navigate(['courses', this.courseId, 'exams', this.exam.id, 'test-exam', this.studentExam.id]);
        }
    }
}
