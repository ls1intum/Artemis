import { ChangeDetectionStrategy, Component, OnDestroy, inject, input, output } from '@angular/core';
import { EMPTY, Subject, from } from 'rxjs';
import { faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CancellationModalComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { EditTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { catchError, takeUntil } from 'rxjs/operators';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';

@Component({
    selector: 'jhi-tutorial-group-session-row-buttons',
    templateUrl: './tutorial-group-session-row-buttons.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, DeleteButtonDirective, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class TutorialGroupSessionRowButtonsComponent implements OnDestroy {
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private modalService = inject(NgbModal);

    ngUnsubscribe = new Subject<void>();

    course = input.required<Course>();
    tutorialGroup = input.required<TutorialGroup>();
    tutorialGroupSession = input.required<TutorialGroupSession>();

    readonly tutorialGroupSessionDeleted = output<void>();
    readonly tutorialGroupEdited = output<void>();
    readonly cancelOrActivatePressed = output<void>();

    tutorialGroupSessionStatus = TutorialGroupSessionStatus;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faWrench = faWrench;
    faUsers = faUsers;
    faTrash = faTrash;

    deleteTutorialGroupSession = () => {
        this.tutorialGroupSessionService
            .delete(this.course().id!, this.tutorialGroup().id!, this.tutorialGroupSession().id!)
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
        modalRef.componentInstance.tutorialGroupId = this.tutorialGroup().id!;

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
