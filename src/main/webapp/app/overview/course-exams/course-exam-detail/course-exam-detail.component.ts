import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import * as moment from 'moment';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-course-exam-detail',
    templateUrl: './course-exam-detail.component.html',
})
export class CourseExamDetailComponent {
    @Input() exam: Exam;
    @Input() course: Course;

    constructor(private router: Router) {}

    /**
     * navigate to /courses/:courseid/exams/:examId
     */
    openExam(): void {
        this.router.navigate(['courses', this.course.id, 'exams', this.exam.id]);
        // TODO: store the (plain) selected exam in the some service so that it can be obtained on other pages
        // also make sure that the exam objects does not contain the course and all exercises
    }

    /**
     * calculate the duration in seconds between the start and end date of the exam
     */
    get examDuration(): number {
        return round(moment.duration(moment(this.exam.endDate).diff(moment(this.exam.startDate), 'seconds')));
    }
}
