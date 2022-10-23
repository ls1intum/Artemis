import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Subject } from 'rxjs';
import { faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CancellationModalComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-tutorial-group-session-row-buttons',
    templateUrl: './tutorial-group-session-row-buttons.component.html',
})
export class TutorialGroupSessionRowButtonsComponent {
    @Input() course: Course;
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

    constructor(private tutorialGroupSessionService: TutorialGroupSessionService, private modalService: NgbModal) {}

    deleteTutorialGroupSession = () => {
        this.tutorialGroupSessionService.delete(this.course.id!, this.tutorialGroupId, this.tutorialGroupSession.id!).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.tutorialGroupSessionDeleted.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    };

    openCancellationModal(session: TutorialGroupSession): void {
        const modalRef = this.modalService.open(CancellationModalComponent);
        modalRef.componentInstance.tutorialGroupSession = session;
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.tutorialGroupId = this.tutorialGroupId;

        modalRef.result.then((result) => {
            if (result === 'confirmed') {
                this.cancelOrActivatePressed.emit();
            }
        });
    }
}
