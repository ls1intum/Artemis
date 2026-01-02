import { ChangeDetectionStrategy, Component, OnDestroy, inject, input, output, viewChild } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { faCalendarAlt, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupSessionsManagementComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { takeUntil } from 'rxjs/operators';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { RegisteredStudentsComponent } from 'app/tutorialgroup/manage/registered-students/registered-students.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';

@Component({
    selector: 'jhi-tutorial-group-row-buttons',
    templateUrl: './tutorial-group-row-buttons.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, RouterLink, DeleteButtonDirective, TutorialGroupSessionsManagementComponent, RegisteredStudentsComponent],
})
export class TutorialGroupRowButtonsComponent implements OnDestroy {
    private tutorialGroupsService = inject(TutorialGroupsService);

    ngUnsubscribe = new Subject<void>();

    readonly isAtLeastInstructor = input(false);
    readonly course = input.required<Course>();
    readonly tutorialGroup = input.required<TutorialGroup>();

    readonly tutorialGroupDeleted = output<void>();
    readonly registrationsChanged = output<void>();
    readonly attendanceUpdated = output<void>();

    readonly sessionManagementDialog = viewChild<TutorialGroupSessionsManagementComponent>('sessionManagementDialog');
    readonly registeredStudentsDialog = viewChild<RegisteredStudentsComponent>('registeredStudentsDialog');

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faWrench = faWrench;
    faUsers = faUsers;
    faTrash = faTrash;
    faCalendar = faCalendarAlt;

    public constructor() {}

    openSessionDialog(event: MouseEvent) {
        event.stopPropagation();
        this.sessionManagementDialog()?.open();
    }

    onSessionDialogClosed(): void {
        this.attendanceUpdated.emit();
    }

    openRegistrationDialog(event: MouseEvent) {
        event.stopPropagation();
        this.registeredStudentsDialog()?.open();
    }

    onRegistrationDialogClosed(): void {
        this.registrationsChanged.emit();
    }

    deleteTutorialGroup = () => {
        this.tutorialGroupsService
            .delete(this.course().id!, this.tutorialGroup().id!)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    this.tutorialGroupDeleted.emit();
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
    };

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.dialogErrorSource.unsubscribe();
    }
}
