import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ApollonDiagramListComponent } from '../list/apollon-diagram-list.component';
import { ApollonDiagramDetailComponent } from '../detail/apollon-diagram-detail.component';

@Component({
    selector: 'jhi-apollon-diagram-import-dialog',
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './apollon-diagram-import-dialog.component.html',
    providers: [ApollonDiagramService],
    imports: [ApollonDiagramListComponent, ApollonDiagramDetailComponent],
})
export class ApollonDiagramImportDialogComponent {
    private activeModal = inject(NgbActiveModal);

    courseId = input.required<number>();

    isInEditView = signal(false);
    apollonDiagramDetailId = signal<number | undefined>(undefined);

    handleDetailOpen(id: number) {
        this.isInEditView.set(true);
        this.apollonDiagramDetailId.set(id);
    }

    handleDetailClose(dndQuestion?: DragAndDropQuestion) {
        if (dndQuestion) {
            this.activeModal.close(dndQuestion);
        } else {
            this.isInEditView.set(false);
        }
    }

    closeModal() {
        this.activeModal.dismiss();
    }
}
