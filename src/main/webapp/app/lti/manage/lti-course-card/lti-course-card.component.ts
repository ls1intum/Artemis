import { Component, computed, input } from '@angular/core';
import { Course } from 'app/course/shared/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ImageComponent } from 'app/shared-ui/image/image.component';
import { RouterLink } from '@angular/router';
import { TumAetUiCardComponent } from '@tumaet/ui-angular/card';
import { getContrastingTextColor } from 'app/foundation/util/color.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-overview-lti-course-card',
    templateUrl: './lti-course-card.component.html',
    styleUrl: './lti-course-card.component.css',
    imports: [RouterLink, ImageComponent, TranslateDirective, ArtemisDatePipe, TumAetUiCardComponent],
})
export class LtiCourseCardComponent {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    course = input.required<Course>();
    readonly courseColor = computed(() => this.course()?.color || this.ARTEMIS_DEFAULT_COLOR);
    readonly contentColor = computed(() => getContrastingTextColor(this.courseColor()));
}
