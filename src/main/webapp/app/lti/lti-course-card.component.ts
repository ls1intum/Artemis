import { Component, Input, OnChanges } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { CachingStrategy } from 'app/shared/image/secured-image.component';

@Component({
    selector: 'jhi-overview-lti-course-card',
    templateUrl: './lti-course-card.component.html',
    styleUrls: ['../overview/course-card.scss'],
})
export class LtiCourseCardComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    @Input() course: Course;
    CachingStrategy = CachingStrategy;
    courseColor: string;

    ngOnChanges() {
        this.courseColor = this.course.color || this.ARTEMIS_DEFAULT_COLOR;
    }
}
