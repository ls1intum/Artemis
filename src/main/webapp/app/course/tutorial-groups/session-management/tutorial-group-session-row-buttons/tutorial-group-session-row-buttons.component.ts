import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Subject } from 'rxjs';
import { faCalendarAlt, faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CancellationModalComponent } from 'app/course/tutorial-groups/session-management/cancellation-modal/cancellation-modal.component';

@Component({
    selector: 'jhi-tutorial-group-session-row-buttons-component',
    templateUrl: './tutorial-group-session-row-buttons.component.html',
    styleUrls: ['./tutorial-group-session-row-buttons.component.scss'],
})
export class TutorialGroupSessionRowButtonsComponent {
    @Input() courseId: number;
    @Input() tutorialGroupId: number;
    @Input() tutorialGroupSession: TutorialGroupSession;

    @Output() tutorialGroupSessionDeleted = new EventEmitter<void>();
    @Output() cancelOrActivatePressed = new EventEmitter<void>();

    tutorialGroupSessionStatus = TutorialGroupSessionStatus;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faWrench = faWrench;
    faUsers = faUsers;
    faTimes = faTimes;
    faCalendar = faCalendarAlt;

    constructor(private tutorialGroupSessionService: TutorialGroupSessionService, private modalService: NgbModal) {}

    deleteTutorialGroupSession = () => {
        // ToDo: implement
        return null;
    };

    openCancellationModal(session: TutorialGroupSession): void {
        const modalRef = this.modalService.open(CancellationModalComponent);
        modalRef.componentInstance.tutorialGroupSession = session;
        modalRef.result.then((result) => {
            if (result === 'confirmed') {
                this.cancelOrActivatePressed.emit();
            }
        });
    }
}
