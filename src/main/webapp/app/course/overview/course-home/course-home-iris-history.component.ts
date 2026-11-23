import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseHome } from './course-home.model';
import { courseHomeTarget } from './course-home-target.util';

@Component({
    selector: 'jhi-course-home-iris-history',
    templateUrl: './course-home-iris-history.component.html',
    imports: [RouterLink, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseHomeIrisHistoryComponent {
    readonly courseId = input.required<number>();
    readonly sessions = input.required<CourseHome['irisSessions']>();
    protected readonly target = courseHomeTarget;
}
