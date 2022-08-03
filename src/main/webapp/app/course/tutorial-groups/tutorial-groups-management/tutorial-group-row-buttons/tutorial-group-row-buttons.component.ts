import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group.model';
import { faWrench } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-tutorial-group-row-buttons',
    templateUrl: './tutorial-group-row-buttons.component.html',
})
export class TutorialGroupRowButtonsComponent {
    @Input() courseId: number;
    @Input() tutorialGroup: TutorialGroup;

    faWrench = faWrench;
}
