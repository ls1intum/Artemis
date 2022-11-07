import { Component, HostBinding, Input, TemplateRef, ViewEncapsulation } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { getDayTranslationKey } from '../../weekdays';
import { Language } from 'app/entities/course.model';

@Component({
    selector: '[jhi-tutorial-group-row]',
    templateUrl: './tutorial-group-row.component.html',
    styleUrls: ['./tutorial-group-row.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TutorialGroupRowComponent {
    @HostBinding('class') class = 'tutorial-group-row';

    @Input()
    showIdColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() tutorialGroup: TutorialGroup;

    @Input()
    tutorialGroupClickHandler: (tutorialGroup: TutorialGroup) => void;

    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;

    getDayTranslationKey = getDayTranslationKey;
}
