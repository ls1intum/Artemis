import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: 'jhi-tutorial-groups-table',
    template: `
        <div>
            <div *ngFor="let tutorialGroup of tutorialGroups">
                <ng-template [ngTemplateOutlet]="extraColumn" [ngTemplateOutletContext]="{ $implicit: tutorialGroup }"></ng-template>
            </div>
        </div>
    `,
})
export class TutorialGroupsTableStubComponent {
    @Input()
    showIdColumn = false;

    @Input()
    tutorialGroups: TutorialGroup[] = [];

    @Input()
    courseId: number;

    @Input()
    tutorialGroupClickHandler: (tutorialGroup: TutorialGroup) => void;

    @ContentChild(TemplateRef) extraColumn: TemplateRef<any>;
}
