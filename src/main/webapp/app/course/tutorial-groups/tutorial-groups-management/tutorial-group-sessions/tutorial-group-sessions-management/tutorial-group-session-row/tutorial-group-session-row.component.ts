import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';

@Component({
    selector: '[jhi-session-row]',
    templateUrl: './tutorial-group-session-row.component.html',
})
export class TutorialGroupSessionRowComponent implements OnChanges {
    tutorialGroupSessionStatus = TutorialGroupSessionStatus;

    @Input() session: TutorialGroupSession;
    @Input() courseId: number;
    @Input() tutorialGroupId: number;
    @Input() timeZone: string;

    @Output() actionPerformed = new EventEmitter<void>();

    isCancelled = false;
    hasSchedule = false;

    ngOnChanges() {
        if (this.session) {
            this.isCancelled = this.session.status === TutorialGroupSessionStatus.CANCELLED;
            this.hasSchedule = this.session.tutorialGroupSchedule !== undefined;
        }
    }
}
