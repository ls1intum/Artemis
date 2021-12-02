import { Component, HostBinding, Input } from '@angular/core';
import dayjs from 'dayjs';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
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
    @Input() extendedLink = false;

    // Icons
    faChalkboardTeacher = faChalkboardTeacher;

    constructor(private router: Router, private route: ActivatedRoute) {}

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

    showDetails() {
        const lectureToAttach = {
            ...this.lecture,
            startDate: this.lecture.startDate ? this.lecture.startDate.valueOf() : undefined,
            endDate: this.lecture.endDate ? this.lecture.endDate.valueOf() : undefined,
            course: {
                id: this.course.id,
            },
        };
        if (this.extendedLink) {
            this.router.navigate(['courses', this.course.id, 'lectures', this.lecture.id], {
                state: {
                    lecture: lectureToAttach,
                },
            });
        } else {
            this.router.navigate([this.lecture.id], {
                relativeTo: this.route,
                state: {
                    lecture: lectureToAttach,
                },
            });
        }
    }
}
