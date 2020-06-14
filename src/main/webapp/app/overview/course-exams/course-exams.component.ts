import { Component, OnInit, OnDestroy } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Exam } from 'app/entities/exam.model';
import * as moment from 'moment';

@Component({
    selector: 'jhi-course-exams',
    templateUrl: './course-exams.component.html',
    styleUrls: ['./course-exams.scss'],
})
export class CourseExamsComponent implements OnInit, OnDestroy {
    courseId: number;
    public course: Course | null;
    private paramSubscription: Subscription;

    constructor(private route: ActivatedRoute, private courseCalculationService: CourseScoreCalculationService) {}

    /**
     * subscribe to changes in the course and fetch course by the path parameter
     */
    ngOnInit(): void {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
    }

    /**
     * unsubscribe from all unsubscriptions
     */
    ngOnDestroy(): void {
        this.paramSubscription.unsubscribe();
    }

    /**
     * check for given exam if it is visible
     * @param {Exam} exam
     */
    isVisible(exam: Exam): boolean {
        return exam.visibleDate ? moment(exam.visibleDate).isBefore(moment()) : false;
    }
}
