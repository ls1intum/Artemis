import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsRegistrationImportDialog } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';

@Component({
    selector: 'jhi-tutorial-groups-import-button',
    templateUrl: './tutorial-groups-import-button.component.html',
})
export class TutorialGroupsImportButtonComponent {
    @Input() courseId: number;

    @Output() importFinished: EventEmitter<void> = new EventEmitter();

    // Icons
    faPlus = faPlus;

    constructor(private modalService: NgbModal) {}

    openTutorialGroupImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(TutorialGroupsRegistrationImportDialog, { size: 'xl', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;

        modalRef.result.then(
            () => this.importFinished.emit(),
            () => {},
        );
    }
}
