import { Component, Input, OnChanges, TemplateRef } from '@angular/core';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: '[jhi-session-row]',
    templateUrl: './tutorial-group-session-row.component.html',
})
export class TutorialGroupSessionRowComponent implements OnChanges {
    @Input()
    showIdColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() session: TutorialGroupSession;
    @Input() tutorialGroup: TutorialGroup;

    @Input() timeZone?: string = undefined;

    isCancelled = false;
    hasSchedule = false;

    ngOnChanges() {
        if (this.session) {
            this.isCancelled = this.session.status === TutorialGroupSessionStatus.CANCELLED;
            this.hasSchedule = !!this.session.tutorialGroupSchedule;
        }
    }
}
