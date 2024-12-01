import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { UsersImportDialogComponent } from 'app/shared/user-import/users-import-dialog.component';
import { CourseGroup } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam/exam.model';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: 'jhi-user-import-button',
    template: `
        <jhi-button [btnType]="buttonType" [btnSize]="buttonSize" [icon]="faFileImport" [title]="'artemisApp.importUsers.buttonLabel'" (onClick)="openUsersImportDialog($event)" />
    `,
})
export class UsersImportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() tutorialGroup: TutorialGroup | undefined = undefined;
    @Input() examUserMode: boolean;
    @Input() adminUserMode: boolean;
    @Input() courseGroup: CourseGroup;
    @Input() courseId: number;
    @Input() buttonSize: ButtonSize = ButtonSize.MEDIUM;
    @Input() buttonType: ButtonType = ButtonType.PRIMARY;
    @Input() exam: Exam;

    @Output() finish: EventEmitter<void> = new EventEmitter();

    // Icons
    faFileImport = faFileImport;

    constructor(private modalService: NgbModal) {}

    /**
     * Open up import dialog for users
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openUsersImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(UsersImportDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.courseGroup = this.courseGroup;
        modalRef.componentInstance.exam = this.exam;
        modalRef.componentInstance.tutorialGroup = this.tutorialGroup;
        modalRef.componentInstance.examUserMode = this.examUserMode;
        modalRef.componentInstance.adminUserMode = this.adminUserMode;
        modalRef.result.then(
            () => this.finish.emit(),
            () => {},
        );
    }
}
