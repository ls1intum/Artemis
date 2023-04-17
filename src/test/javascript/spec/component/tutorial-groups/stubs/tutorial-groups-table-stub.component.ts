import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course } from 'app/entities/course.model';

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

    tutorialGroupsSplitAcrossMultipleCampuses = false;
    mixOfOfflineAndOfflineTutorialGroups = false;
    mifOfDifferentLanguages = false;

    @Input()
    showChannelColumn = false;

    @Input()
    tutorialGroups: TutorialGroup[] = [];

    @Input()
    course: Course;

    @Input()
    timeZone: string;

    @Input()
    tutorialGroupClickHandler: (tutorialGroup: TutorialGroup) => void;

    @ContentChild(TemplateRef, { static: true }) extraColumn: TemplateRef<any>;
}

@Component({
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: '[jhi-tutorial-group-row]',
    template: `
        <div>
            <div *ngIf="showIdColumn">
                <span>{{ tutorialGroup.id }}</span>
            </div>
            <div *ngIf="showChannelColumn">
                <span>{{ tutorialGroup?.channel?.name || '' }}</span>
            </div>
            <div *ngIf="extraColumn">
                <ng-template [ngTemplateOutlet]="extraColumn" [ngTemplateOutletContext]="{ $implicit: tutorialGroup }"></ng-template>
            </div>
        </div>
    `,
})
export class TutorialGroupRowStubComponent {
    @Input()
    showIdColumn = false;

    @Input()
    tutorialGroupsSplitAcrossMultipleCampuses = false;
    @Input()
    mixOfOfflineAndOfflineTutorialGroups = false;
    @Input()
    mifOfDifferentLanguages = false;

    @Input()
    showChannelColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() tutorialGroup: TutorialGroup;

    @Input() course: Course;

    @Input() timeZone: string;

    @Input()
    tutorialGroupClickHandler: (tutorialGroup: TutorialGroup) => void;
}
