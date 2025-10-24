import { Component, InputSignal, OutputEmitterRef, inject, input, output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faPlus, faThLarge, faUpload } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { StudentsRoomDistributionDialogComponent } from 'app/exam/manage/students/room-distribution/students-room-distribution-dialog.component';

@Component({
    selector: 'jhi-students-room-distribution-button',
    template: `
        <jhi-button
            [btnType]="buttonType()"
            [btnSize]="buttonSize()"
            [icon]="faThLarge"
            [title]="'artemisApp.exam.examUsers.roomDistribute'"
            (onClick)="openRoomDistributionDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class StudentsRoomDistributionButtonComponent {
    private modalService = inject(NgbModal);

    courseId: InputSignal<number> = input.required();
    exam: InputSignal<Exam> = input.required();
    buttonType: InputSignal<ButtonType> = input(ButtonType.PRIMARY);
    buttonSize: InputSignal<ButtonSize> = input(ButtonSize.MEDIUM);

    distributionDone: OutputEmitterRef<void> = output();

    // Icons
    readonly faPlus = faPlus;
    readonly faUpload = faUpload;
    readonly faThLarge = faThLarge;

    /**
     * Open the room distribution dialog for assigning exam users to rooms
     *
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openRoomDistributionDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(StudentsRoomDistributionDialogComponent, {
            keyboard: true,
            size: 'lg',
            backdrop: 'static',
        });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.exam = this.exam;
        modalRef.result.then(
            () => this.distributionDone.emit(),
            () => {},
        );
    }
}
