import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: 'jhi-tutorial-group-detail',
    template: `
        <div>
            <div>
                <ng-template [ngTemplateOutlet]="header" [ngTemplateOutletContext]="{ $implicit: tutorialGroup }"></ng-template>
            </div>
        </div>
    `,
})
export class TutorialGroupDetailStubComponent {
    @ContentChild(TemplateRef) header: TemplateRef<any>;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    courseClickHandler: () => void;

    @Input()
    registrationClickHandler: () => void;

    @Input()
    timeZone?: string = undefined;
}
