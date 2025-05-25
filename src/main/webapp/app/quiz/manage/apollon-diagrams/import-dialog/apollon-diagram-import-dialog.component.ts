import { Component, Input, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ApollonDiagramListComponent } from '../list/apollon-diagram-list.component';
import { ApollonDiagramDetailComponent } from '../detail/apollon-diagram-detail.component';

@Component({
    selector: 'jhi-apollon-diagram-import-dialog',
    templateUrl: './apollon-diagram-import-dialog.component.html',
    providers: [ApollonDiagramService],
    imports: [ApollonDiagramListComponent, ApollonDiagramDetailComponent],
})
export class ApollonDiagramImportDialogComponent {
    private activeModal = inject(NgbActiveModal);

    @Input()
    courseId: number;

    isInEditView = false;
    apollonDiagramDetailId: number;

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
