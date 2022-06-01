import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { UsersImportDialogComponent } from 'app/shared/import/users-import-dialog.component';
import { CourseGroup } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-user-import-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="faPlus"
            [title]="'artemisApp.importUsers.buttonLabel'"
            (onClick)="openUsersImportDialog($event)"
        ></jhi-button>
    `,
})
export class UsersImportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() courseGroup: CourseGroup;
    @Input() courseId: number;
    @Input() buttonSize: ButtonSize = ButtonSize.MEDIUM;
    @Input() exam: Exam;

    @Output() finish: EventEmitter<void> = new EventEmitter();

    // Icons
    faPlus = faPlus;

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
        modalRef.result.then(
            () => this.finish.emit(),
            () => {},
        );
    }
}
