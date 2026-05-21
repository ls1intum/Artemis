import { Component, input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { faChalkboardTeacher } from '@fortawesome/free-solid-svg-icons';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';

@Component({
    selector: 'jhi-course-lecture-row',
    templateUrl: './course-lecture-row.component.html',
    styleUrls: ['../../../../core/course/overview/course-exercises/course-exercise-row/course-exercise-row.scss'],
    imports: [RouterLink, FaIconComponent, NgbTooltip, NgClass, TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe, ArtemisTimeAgoPipe],
    host: { class: 'exercise-row' },
})
export class CourseLectureRowComponent {
    lecture = input.required<Lecture>();
    course = input.required<Course>();

    // Icons
    faChalkboardTeacher = faChalkboardTeacher;

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
