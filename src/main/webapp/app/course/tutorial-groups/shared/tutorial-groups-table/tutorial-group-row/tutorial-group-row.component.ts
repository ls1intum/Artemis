import { ChangeDetectionStrategy, Component, HostBinding, Input, TemplateRef, ViewEncapsulation } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course, Language } from 'app/entities/course.model';

@Component({
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: '[jhi-tutorial-group-row]',
    templateUrl: './tutorial-group-row.component.html',
    styleUrls: ['./tutorial-group-row.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupRowComponent {
    readonly Math = Math;
    @HostBinding('class') class = 'tutorial-group-row';

    @Input()
    showIdColumn = false;

    @Input()
    showChannelColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() tutorialGroup: TutorialGroup;

    @Input() course: Course;

    @Input() timeZone?: string = undefined;

    @Input()
    tutorialGroupClickHandler: (tutorialGroup: TutorialGroup) => void;

    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;
}
