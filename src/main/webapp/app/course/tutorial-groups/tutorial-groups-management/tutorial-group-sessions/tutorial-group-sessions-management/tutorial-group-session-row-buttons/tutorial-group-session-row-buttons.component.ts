import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { EMPTY, Subject, from } from 'rxjs';
import { faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CancellationModalComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { catchError, takeUntil } from 'rxjs/operators';

@Component({
    selector: 'jhi-tutorial-group-session-row-buttons',
    templateUrl: './tutorial-group-session-row-buttons.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupSessionRowButtonsComponent implements OnDestroy {
    ngUnsubscribe = new Subject<void>();

    @Input() course: Course;
    @Input() tutorialGroup: TutorialGroup;
    @Input() tutorialGroupSession: TutorialGroupSession;

    @Output() tutorialGroupSessionDeleted = new EventEmitter<void>();
    @Output() tutorialGroupEdited = new EventEmitter<void>();
    @Output() cancelOrActivatePressed = new EventEmitter<void>();

    tutorialGroupSessionStatus = TutorialGroupSessionStatus;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faWrench = faWrench;
    faUsers = faUsers;
    faTimes = faTimes;

    constructor(
        private tutorialGroupSessionService: TutorialGroupSessionService,
        private modalService: NgbModal,
    ) {}

    deleteTutorialGroupSession = () => {
        this.tutorialGroupSessionService
            .delete(this.course.id!, this.tutorialGroup.id!, this.tutorialGroupSession.id!)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    this.tutorialGroupSessionDeleted.emit();
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
    };

    openCancellationDialog(session: TutorialGroupSession): void {
        const modalRef = this.modalService.open(CancellationModalComponent, { size: 'lg', scrollable: false, backdrop: 'static', animation: false });
        modalRef.componentInstance.tutorialGroupSession = session;
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.tutorialGroupId = this.tutorialGroup.id!;

        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((result) => {
                if (result === 'confirmed') {
                    this.cancelOrActivatePressed.emit();
                }
            });
    }

    openEditSessionDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(EditTutorialGroupSessionComponent, { size: 'lg', scrollable: false, backdrop: 'static', animation: false });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.tutorialGroup = this.tutorialGroup;
        modalRef.componentInstance.tutorialGroupSession = this.tutorialGroupSession;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.tutorialGroupEdited.emit();
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.dialogErrorSource.unsubscribe();
    }
}
