import { Component, OnChanges, input } from '@angular/core';
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
export class LtiCourseCardComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly course = input.required<Course>();
    CachingStrategy = CachingStrategy;
    courseColor: string;

    ngOnChanges() {
        this.courseColor = this.course().color || this.ARTEMIS_DEFAULT_COLOR;
    }
}
