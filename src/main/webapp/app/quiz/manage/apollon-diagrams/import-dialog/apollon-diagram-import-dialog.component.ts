import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
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
export class ApollonDiagramImportDialogComponent implements OnInit {
    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);

    readonly courseId = signal<number>(0);

    isInEditView = signal(false);
    apollonDiagramDetailId = signal<number | undefined>(undefined);

    ngOnInit() {
        this.courseId.set(this.dialogConfig.data.courseId);
    }

    handleDetailOpen(id: number) {
        this.isInEditView.set(true);
        this.apollonDiagramDetailId.set(id);
    }

    handleDetailClose(dndQuestion?: DragAndDropQuestion) {
        if (dndQuestion) {
            this.dialogRef.close(dndQuestion);
        } else {
            this.isInEditView.set(false);
        }
    }

    closeModal() {
        this.dialogRef.close();
    }
}
