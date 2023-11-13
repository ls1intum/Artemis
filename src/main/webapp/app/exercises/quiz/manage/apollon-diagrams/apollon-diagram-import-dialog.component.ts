import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { faArrowLeft, faX } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-apollon-diagram-import-dialog',
    templateUrl: './apollon-diagram-import-dialog.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramImportDialogComponent {
    @Input()
    courseId: number;

    isInEditView = false;
    apollonDiagramDetailId: number;

    faArrow = faArrowLeft;
    faX = faX;

    constructor(private activeModal: NgbActiveModal) {}

    handleDetailOpen(id: number) {
        this.isInEditView = true;
        this.apollonDiagramDetailId = id;
    }

    handleDetailClose(dndQuestion?: DragAndDropQuestion) {
        this.activeModal.close(dndQuestion);
    }
}
