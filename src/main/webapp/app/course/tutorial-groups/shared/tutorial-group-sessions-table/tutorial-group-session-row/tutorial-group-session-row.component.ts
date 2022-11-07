import { Component, HostBinding, Input, OnChanges, TemplateRef, ViewEncapsulation } from '@angular/core';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: '[jhi-session-row]',
    templateUrl: './tutorial-group-session-row.component.html',
    styleUrls: ['./tutorial-group-session-row.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TutorialGroupSessionRowComponent implements OnChanges {
    @HostBinding('class') class = 'tutorial-group-session-row';

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
