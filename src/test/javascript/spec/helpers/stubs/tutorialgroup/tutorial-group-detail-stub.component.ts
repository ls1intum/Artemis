import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';

@Component({
    selector: 'jhi-tutorial-group-detail',
    template: `
        <div>
            <div>
                <ng-template [ngTemplateOutlet]="header" [ngTemplateOutletContext]="{ $implicit: tutorialGroup }" />
            </div>
        </div>
    `,
})
export class TutorialGroupDetailStubComponent {
    @ContentChild(TemplateRef, { static: true }) header: TemplateRef<any>;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    courseClickHandler: () => void;

    @Input()
    registrationClickHandler: () => void;

    @Input()
    timeZone?: string = undefined;

    @Input()
    course: Course;
}
