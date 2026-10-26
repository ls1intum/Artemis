import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TumAetUiAvatarComponent, TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { addPublicFilePrefix } from 'app/app.constants';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseHomeTeachingTeam } from './course-home.model';

@Component({
    selector: 'jhi-course-home-teaching-team',
    templateUrl: './course-home-teaching-team.component.html',
    imports: [TumAetUiAvatarComponent, TumAetUiButtonDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseHomeTeachingTeamComponent {
    readonly team = input.required<CourseHomeTeachingTeam>();
    readonly loading = input(false);
    readonly failed = input(false);
    readonly loadMore = output<void>();
    protected readonly image = addPublicFilePrefix;
}
