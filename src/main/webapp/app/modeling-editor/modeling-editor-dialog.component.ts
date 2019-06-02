import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { map, tap } from 'rxjs/operators';
import { DiagramType, UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from 'app/modeling-editor/modeling-editor.component';
import { ApollonDiagram, ApollonDiagramService } from 'app/entities/apollon-diagram';

@Component({
    selector: 'jhi-modeling-editor-dialog',
    templateUrl: './modeling-editor-dialog.component.html',
})
export class ModelingEditorDialogComponent {
    DiagramType = DiagramType;
    @ViewChild(ModelingEditorComponent) editor: ModelingEditorComponent;
    @Input()
    get diagramId() {
        return this.diagramIdValue;
    }
    @Input()
    diagramType = DiagramType.ClassDiagram;
    @Input()
    readOnly = false;
    @Output()
    onModelSave = new EventEmitter<ApollonDiagram>();

    diagramIdValue: number;
    diagram: ApollonDiagram;
    umlModel: UMLModel;
    isLoading = false;
    isSaving = false;

    set diagramId(diagramId: number) {
        this.diagramIdValue = diagramId;
        this.isLoading = true;
        this.apollonDiagramService
            .find(this.diagramId)
            .pipe(map(res => res && res.body))
            .subscribe((diagram: ApollonDiagram) => {
                this.diagram = diagram;
                this.diagramType = diagram.diagramType;
                this.umlModel = JSON.parse(diagram.jsonRepresentation);
                this.isLoading = false;
            });
    }

    constructor(private activeModal: NgbActiveModal, private apollonDiagramService: ApollonDiagramService) {}

    selectDiagramType(diagramType: DiagramType) {
        this.diagramType = diagramType;
    }

    onSave() {
        this.isSaving = true;
        // Update existing model
        if (this.diagram) {
            const updatedDiagram = { ...this.diagram, jsonRepresentation: JSON.stringify(this.editor.getCurrentModel()) };
            this.apollonDiagramService
                .update(updatedDiagram)
                .pipe(
                    map(res => res && res.body),
                    tap(diagram => this.onModelSave.emit(diagram)),
                )
                .subscribe(() => (this.isSaving = false));
        } else {
            // Create new model
            const diagram = new ApollonDiagram(DiagramType.ClassDiagram);
            diagram.jsonRepresentation = JSON.stringify(this.editor.getCurrentModel());
            diagram.title = 'new diagram';
            this.apollonDiagramService
                .create(diagram)
                .pipe(
                    map(res => res && res.body),
                    tap(diagram => this.onModelSave.emit(diagram)),
                )
                .subscribe(() => (this.isSaving = false));
        }
    }

    onCancel() {
        this.activeModal.dismiss('cancel');
    }
}
