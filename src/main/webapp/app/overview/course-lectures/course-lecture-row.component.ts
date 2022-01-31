import { Component, HostBinding, Input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { faChalkboardTeacher } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-lecture-row',
    templateUrl: './course-lecture-row.component.html',
    styleUrls: ['../course-exercises/course-exercise-row.scss'],
})
export class CourseLectureRowComponent {
    @HostBinding('class') classes = 'exercise-row';
    @Input() lecture: Lecture;
    @Input() course: Course;

    // Icons
    faChalkboardTeacher = faChalkboardTeacher;

    constructor() {}

    getUrgentClass(date?: dayjs.Dayjs) {
        if (!date) {
            return '';
        }
        const remainingDays = date.diff(dayjs(), 'days');
        if (0 <= remainingDays && remainingDays < 7) {
            return 'text-danger';
        }
        return '';
    }
}
