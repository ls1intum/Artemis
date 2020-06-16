import { Component, OnInit, OnDestroy } from '@angular/core';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import * as moment from 'moment';

@Component({
    selector: 'jhi-exam-participation',
    templateUrl: './exam-participation.component.html',
    styleUrls: ['./exam-participation.scss'],
})
export class ExamParticipationComponent implements OnInit, OnDestroy {
    course: Course | null;
    courseId: number;
    private paramSubscription: Subscription;
    exam: Exam;
    examId: number;

    constructor(private courseCalculationService: CourseScoreCalculationService, private route: ActivatedRoute) {}

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
            this.examId = parseInt(params['examId'], 10);
        });

        // load exam like this until service is ready
        this.course = this.courseCalculationService.getCourse(this.courseId);
        this.exam = this.course!.exams.filter((exam) => exam.id === this.examId)[0]!;
    }

    /**
     * check if exam is over
     */
    isOver(): boolean {
        if (!this.exam) {
            return false;
        }
        return this.exam.endDate ? moment(this.exam.endDate).isBefore(moment()) : false;
    }
    /**
     * check if exam is visible
     */
    isVisible(): boolean {
        if (!this.exam) {
            return false;
        }
        return this.exam.visibleDate ? moment(this.exam.visibleDate).isBefore(moment()) : false;
    }

    ngOnDestroy(): void {
        this.paramSubscription.unsubscribe();
    }
}
