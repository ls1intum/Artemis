import { Component, effect, input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { CachingStrategy, SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { RouterLink } from '@angular/router';
import { NgStyle } from '@angular/common';
import { getContrastingTextColor } from 'app/shared/util/color.utils';

@Component({
    selector: 'jhi-overview-lti-course-card',
    templateUrl: './lti-course-card.component.html',
    styleUrls: ['../../../core/course/overview/course-card/course-card.scss'],
    imports: [RouterLink, NgStyle, SecuredImageComponent],
})
export class LtiCourseCardComponent {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    course = input.required<Course>();
    CachingStrategy = CachingStrategy;
    courseColor: string;
    contentColor: string;

    constructor() {
        effect(() => {
            const courseValue = this.course();
            this.courseColor = courseValue?.color || this.ARTEMIS_DEFAULT_COLOR;
            this.contentColor = getContrastingTextColor(this.courseColor);
            console.log("desc:" + this.course().description);
            console.log("date:" + this.course().startDate);
            console.log(courseValue);
        });
    }
}
