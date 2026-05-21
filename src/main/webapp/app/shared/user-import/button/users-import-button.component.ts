import { Component, input, output, viewChild } from '@angular/core';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { UsersImportDialogComponent } from 'app/shared/user-import/dialog/users-import-dialog.component';
import { CourseGroup } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

@Component({
    selector: 'jhi-user-import-button',
    template: `
        <jhi-button
            [btnType]="buttonType()"
            [btnSize]="buttonSize()"
            [icon]="faFileImport"
            [title]="'artemisApp.importUsers.buttonLabel'"
            (onClick)="openUsersImportDialog($event)"
        />
        <jhi-users-import-dialog
            #importDialog
            [courseId]="courseId()"
            [courseGroup]="courseGroup()"
            [exam]="exam()"
            [tutorialGroup]="tutorialGroup()"
            [examUserMode]="examUserMode()"
            [adminUserMode]="adminUserMode()"
            (importCompleted)="onImportCompleted()"
        />
    `,
    imports: [ButtonComponent, UsersImportDialogComponent],
})
export class UsersImportButtonComponent {
    readonly importDialog = viewChild<UsersImportDialogComponent>('importDialog');

    readonly tutorialGroup = input<TutorialGroup | undefined>(undefined);
    readonly examUserMode = input<boolean>(false);
    readonly adminUserMode = input<boolean>(false);
    readonly courseGroup = input<CourseGroup>();
    readonly courseId = input<number>();
    readonly buttonSize = input<ButtonSize>(ButtonSize.MEDIUM);
    readonly buttonType = input<ButtonType>(ButtonType.PRIMARY);
    readonly exam = input<Exam>();

    readonly importDone = output<void>();

    // Icons
    faFileImport = faFileImport;

    /**
     * Open up import dialog for users
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openUsersImportDialog(event: MouseEvent) {
        event.stopPropagation();
        this.importDialog()?.open();
    }

    onImportCompleted(): void {
        this.importDone.emit();
    }
}
