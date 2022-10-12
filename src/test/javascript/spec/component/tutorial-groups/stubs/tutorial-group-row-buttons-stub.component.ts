import { Component } from '@angular/core';
import { Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({ selector: 'jhi-tutorial-group-row-buttons', template: '' })
export class TutorialGroupRowButtonsStubComponent {
    @Input() isAtLeastEditor = false;
    @Input() tutorialGroup: TutorialGroup;
    @Input() courseId: number;
}
