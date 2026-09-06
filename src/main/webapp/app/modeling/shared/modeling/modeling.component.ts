import { Component, ElementRef, input, model, viewChild } from '@angular/core';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';

@Component({
    template: '',
})
export abstract class ModelingComponent {
    readonly editorContainer = viewChild<ElementRef<HTMLElement>>('editorContainer');
    umlModel = input<UMLModel>();
    diagramType = input<UMLDiagramType>();
    explanation = model<string>('');
    readOnly = input(false);

    apollonEditor?: ApollonEditor;
}
