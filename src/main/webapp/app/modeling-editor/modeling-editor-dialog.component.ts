import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { map, tap } from 'rxjs/operators';
import { DiagramType, UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from 'app/modeling-editor/modeling-editor.component';
import { ApollonDiagram, ApollonDiagramService } from 'app/entities/apollon-diagram';

@Component({
    selector: 'jhi-modeling-editor-dialog',
    templateUrl: './modeling-editor-dialog.component.html',
})
export class ModelingEditorDialogComponent {
    @ViewChild(ModelingEditorComponent) editor: ModelingEditorComponent;
    @Input()
    umlModel: UMLModel;
    @Input()
    diagramType: DiagramType;
    @Input()
    readOnly = false;
    @Output()
    onModelSave = new EventEmitter<ApollonDiagram>();
    @Output()
    onModelCancel = new EventEmitter<void>();

    isSaving = false;

    constructor(private apollonDiagramService: ApollonDiagramService) {}

    onSave() {
        this.isSaving = true;
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

    onCancel() {
        this.onModelCancel.emit();
    }
}
