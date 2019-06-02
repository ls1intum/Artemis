import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { DiagramType, UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from 'app/modeling-editor/modeling-editor.component';

@Component({
    selector: 'jhi-modeling-editor-dialog',
    templateUrl: './modeling-editor-dialog.component.html',
})
export class ModelingEditorDialogComponent {
    DiagramType = DiagramType;
    @ViewChild(ModelingEditorComponent) editor: ModelingEditorComponent;
    @Input()
    umlModel: UMLModel;
    @Input()
    diagramType = DiagramType.ClassDiagram;
    @Input()
    readOnly = false;
    @Output()
    onModelSave = new EventEmitter<UMLModel>();

    constructor(private activeModal: NgbActiveModal) {}

    selectDiagramType(diagramType: DiagramType) {
        this.diagramType = diagramType;
    }

    onSave() {
        this.onModelSave.emit(this.editor.getCurrentModel());
    }

    onCancel() {
        this.activeModal.dismiss('cancel');
    }
}
