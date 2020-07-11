import { Component, OnInit, OnDestroy } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Exam } from 'app/entities/exam.model';
import * as moment from 'moment';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

@Component({
    selector: 'jhi-course-exams',
    templateUrl: './course-exams.component.html',
    styleUrls: ['./course-exams.scss'],
})
export class CourseExamsComponent implements OnInit, OnDestroy {
    courseId: number;
    public course: Course | null;
    private paramSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;

    constructor(
        private route: ActivatedRoute,
        private courseCalculationService: CourseScoreCalculationService,
        private courseManagementService: CourseManagementService,
        private serverDateService: ArtemisServerDateService,
    ) {}

    /**
     * subscribe to changes in the course and fetch course by the path parameter
     */
    ngOnInit(): void {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);

        this.courseUpdatesSubscription = this.courseManagementService.getCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.courseCalculationService.updateCourse(course);
            this.course = this.courseCalculationService.getCourse(this.courseId);
        });
    }

    /**
     * unsubscribe from all unsubscriptions
     */
    ngOnDestroy(): void {
        this.paramSubscription.unsubscribe();
        this.courseUpdatesSubscription.unsubscribe();
    }

    /**
     * check for given exam if it is visible
     * @param {Exam} exam
     */
    isVisible(exam: Exam): boolean {
        return exam.visibleDate ? moment(exam.visibleDate).isBefore(this.serverDateService.now()) : false;
    }
}
