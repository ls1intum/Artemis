import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { of } from 'rxjs';
import { filter, map, switchMap, tap } from 'rxjs/operators';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from 'app/modeling-editor/modeling-editor.component';
import { ApollonDiagram, ApollonDiagramService } from 'app/entities/apollon-diagram';

@Component({
    selector: 'jhi-modeling-editor-dialog',
    templateUrl: './modeling-editor-dialog.component.html',
})
export class ModelingEditorDialogComponent {
    UMLDiagramType = UMLDiagramType;
    @ViewChild(ModelingEditorComponent, { static: false }) editor: ModelingEditorComponent;
    @Input()
    get diagramId() {
        return this.diagramIdValue;
    }
    @Input()
    diagramType = UMLDiagramType.ClassDiagram;
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
            .pipe(
                tap(console.log),
                map(res => res && res.body),
            )
            .subscribe((diagram: ApollonDiagram) => {
                this.diagram = diagram;
                this.diagramType = diagram.diagramType;
                this.umlModel = JSON.parse(diagram.jsonRepresentation);
                this.isLoading = false;
            });
    }

    constructor(private activeModal: NgbActiveModal, private apollonDiagramService: ApollonDiagramService) {}

    selectDiagramType(diagramType: UMLDiagramType) {
        this.diagramType = diagramType;
    }

    onSave() {
        const currentModel = JSON.stringify(this.editor.getCurrentModel());
        const apollonDiagram = new ApollonDiagram(this.diagramType);
        apollonDiagram.jsonRepresentation = currentModel;
        of(null)
            .pipe(
                tap(() => (this.isSaving = true)),
                switchMap(() =>
                    this.diagramId ? this.apollonDiagramService.update({ ...apollonDiagram, id: this.diagramId }) : this.apollonDiagramService.create(apollonDiagram),
                ),
                map(res => res.body),
                filter(diagram => !!diagram),
            )
            .subscribe((diagram: ApollonDiagram) => {
                this.onModelSave.emit(diagram);
            });
    }

    onCancel() {
        this.activeModal.dismiss('cancel');
    }
}
