import { Component, ElementRef, ViewChild, input, model } from '@angular/core';
import { faGripLines, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { MODELING_EDITOR_MAX_HEIGHT, MODELING_EDITOR_MAX_WIDTH, MODELING_EDITOR_MIN_HEIGHT, MODELING_EDITOR_MIN_WIDTH } from 'app/shared/constants/modeling.constants';
import interact from 'interactjs';

@Component({
    template: '',
})
export abstract class ModelingComponent {
    protected readonly faGripLines = faGripLines;
    protected readonly faGripLinesVertical = faGripLinesVertical;

    @ViewChild('editorContainer', { static: false }) editorContainer: ElementRef;
    @ViewChild('resizeContainer', { static: false }) resizeContainer: ElementRef;
    resizeOptions = input<{
        horizontalResize?: boolean;
        verticalResize?: boolean;
    }>(undefined!);
    umlModel = input<UMLModel>(undefined!);
    diagramType = input<UMLDiagramType>();
    explanation = model<string>(undefined!);
    readOnly = input(false);

    apollonEditor?: ApollonEditor;

    protected setupInteract(): void {
        const resizeOptions = this.resizeOptions();
        if (resizeOptions) {
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: resizeOptions.horizontalResize && '.draggable-right', bottom: resizeOptions.verticalResize, top: false },
                    modifiers: [
                        interact.modifiers!.restrictSize({
                            min: { width: MODELING_EDITOR_MIN_WIDTH, height: MODELING_EDITOR_MIN_HEIGHT },
                            max: { width: MODELING_EDITOR_MAX_WIDTH, height: MODELING_EDITOR_MAX_HEIGHT },
                        }),
                    ],
                    inertia: true,
                })
                .on('resizestart', function (event: any) {
                    event.target.classList.add('card-resizable');
                })
                .on('resizeend', function (event: any) {
                    event.target.classList.remove('card-resizable');
                })
                .on('resizemove', (event: any) => {
                    const target = event.target;
                    const resizeOptionsValue = this.resizeOptions();
                    if (resizeOptionsValue.horizontalResize) {
                        target.style.width = event.rect.width + 'px';
                    }
                    if (resizeOptionsValue.verticalResize) {
                        target.style.height = event.rect.height + 'px';
                    }
                });
        }
    }
}
