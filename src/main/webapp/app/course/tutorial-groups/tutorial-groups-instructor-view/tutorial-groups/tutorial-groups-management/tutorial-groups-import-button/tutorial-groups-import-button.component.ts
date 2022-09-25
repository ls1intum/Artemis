import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsRegistrationImportDialog } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';

@Component({
    selector: 'jhi-tutorial-groups-import-button',
    templateUrl: './tutorial-groups-import-button.component.html',
    styleUrls: ['./tutorial-groups-import-button.component.scss'],
})
export class TutorialGroupsImportButtonComponent implements OnInit {
    @Input() courseId: number;

    @Output() importFinished: EventEmitter<void> = new EventEmitter();

    // Icons
    faPlus = faPlus;

    constructor(private modalService: NgbModal) {}

    ngOnInit(): void {}
    openTutorialGroupImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(TutorialGroupsRegistrationImportDialog, { fullscreen: true, scrollable: true, backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;

        modalRef.result.then(
            () => this.importFinished.emit(),
            () => {},
        );
    }
}
