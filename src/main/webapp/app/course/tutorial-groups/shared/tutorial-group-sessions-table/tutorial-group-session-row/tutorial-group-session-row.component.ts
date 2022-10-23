import { Component, Input, TemplateRef } from '@angular/core';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';

@Component({
    selector: '[jhi-session-row]',
    templateUrl: './tutorial-group-session-row.component.html',
})
export class TutorialGroupSessionRowComponent {
    @Input()
    showIdColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() session: TutorialGroupSession;
    @Input() timeZone?: string = undefined;

    get isCancelled() {
        return this.session?.status === TutorialGroupSessionStatus.CANCELLED;
    }
    get hasSchedule() {
        return !!this.session?.tutorialGroupSchedule;
    }
}
