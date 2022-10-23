import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';

@Component({
    selector: 'jhi-tutorial-group-sessions-table',
    template: `
        <div>
            <div jhi-session-row *ngFor="let session of sessions" [extraColumn]="extraColumn" [session]="session" [timeZone]="timeZone" [showIdColumn]="showIdColumn"></div>
        </div>
    `,
})
export class TutorialGroupSessionsTableStubComponent {
    @ContentChild(TemplateRef) extraColumn: TemplateRef<any>;

    @Input()
    sessions: TutorialGroupSession[] = [];

    @Input()
    timeZone?: string = undefined;

    @Input()
    showIdColumn = false;
}
@Component({
    selector: '[jhi-session-row]',
    template: `
        <div>
            <div *ngIf="showIdColumn">
                <span>{{ session.id }}</span>
            </div>
            <div *ngIf="extraColumn">
                <ng-template [ngTemplateOutlet]="extraColumn" [ngTemplateOutletContext]="{ $implicit: session }"></ng-template>
            </div>
        </div>
    `,
})
export class TutorialGroupSessionRowStubComponent {
    @Input()
    showIdColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() session: TutorialGroupSession;
    @Input() timeZone?: string = undefined;
}
