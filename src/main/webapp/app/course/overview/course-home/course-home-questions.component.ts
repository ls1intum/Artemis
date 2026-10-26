import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseHome } from './course-home.model';
import { courseHomeTarget } from './course-home-target.util';

@Component({
    selector: 'jhi-course-home-questions',
    templateUrl: './course-home-questions.component.html',
    imports: [RouterLink, TumAetUiTagComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseHomeQuestionsComponent {
    readonly courseId = input.required<number>();
    readonly questions = input.required<CourseHome['questions']>();
    protected readonly target = courseHomeTarget;
}
