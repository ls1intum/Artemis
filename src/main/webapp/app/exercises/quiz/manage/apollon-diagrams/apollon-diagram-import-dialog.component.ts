import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ApollonDiagramListComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-list.component';
import { ApollonDiagramDetailComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-detail.component';

@Component({
    selector: 'jhi-apollon-diagram-import-dialog',
    templateUrl: './apollon-diagram-import-dialog.component.html',
    providers: [ApollonDiagramService],
    standalone: true,
    imports: [ApollonDiagramListComponent, ApollonDiagramDetailComponent],
})
export class ApollonDiagramImportDialogComponent {
    @Input()
    courseId: number;

    isInEditView = false;
    apollonDiagramDetailId: number;

    constructor(private activeModal: NgbActiveModal) {}

    handleDetailOpen(id: number) {
        this.isInEditView = true;
        this.apollonDiagramDetailId = id;
    }

    handleDetailClose(dndQuestion?: DragAndDropQuestion) {
        if (dndQuestion) {
            this.activeModal.close(dndQuestion);
        } else {
            this.isInEditView = false;
        }
    }

    closeModal() {
        this.activeModal.dismiss();
    }
}
