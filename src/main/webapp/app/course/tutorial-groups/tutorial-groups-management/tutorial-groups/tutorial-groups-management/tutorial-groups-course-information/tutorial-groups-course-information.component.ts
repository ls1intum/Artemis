import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutorial-groups-course-information',
    templateUrl: './tutorial-groups-course-information.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [SidePanelComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class TutorialGroupsCourseInformationComponent {
    @Input()
    tutorialGroups: TutorialGroup[] = [];

    get totalNumberOfRegistrations(): number {
        return this.tutorialGroups.reduce((acc, tutorialGroup) => acc + (tutorialGroup.numberOfRegisteredUsers ?? 0), 0);
    }
}
