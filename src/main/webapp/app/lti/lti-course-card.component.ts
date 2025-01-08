import { Component, effect, input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { RouterLink } from '@angular/router';
import { NgStyle } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-overview-lti-course-card',
    templateUrl: './lti-course-card.component.html',
    styleUrls: ['../overview/course-card.scss'],
    standalone: true,
    imports: [RouterLink, NgStyle, ArtemisSharedModule],
})
export class LtiCourseCardComponent {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    course = input.required<Course>();
    CachingStrategy = CachingStrategy;
    courseColor: string;

    constructor() {
        effect(() => {
            const courseValue = this.course();
            this.courseColor = courseValue?.color || this.ARTEMIS_DEFAULT_COLOR;
        });
    }
}
