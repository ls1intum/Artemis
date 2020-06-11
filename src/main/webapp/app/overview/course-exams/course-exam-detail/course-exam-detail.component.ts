import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import * as moment from 'moment';

@Component({
    selector: 'jhi-course-exam-detail',
    templateUrl: './course-exam-detail.component.html',
    styleUrls: ['../course-exams.scss'],
})
export class CourseExamDetailComponent {
    @Input() exam: Exam;
    @Input() course: Course;

    constructor(private router: Router) {}

    openExam(): void {
        // TODO: load student exam
        if (moment(this.exam.endDate).isAfter(moment())) {
            this.router.navigate(['courses', this.course.id, 'exam', this.exam.id, 'start'], {
                state: {
                    exam: this.exam,
                },
            });
        } else {
            this.router.navigate(['courses', this.course.id, 'exam', this.exam.id, 'summary'], {
                state: {
                    exam: this.exam,
                },
            });
        }
    }

    get examDuration(): number {
        return Math.round(moment.duration(moment(this.exam.endDate).diff(moment(this.exam.startDate))).asMinutes());
    }
}
