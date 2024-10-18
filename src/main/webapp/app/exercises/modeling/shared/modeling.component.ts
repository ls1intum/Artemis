import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { faGripLines, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { MODELING_EDITOR_MAX_HEIGHT, MODELING_EDITOR_MAX_WIDTH, MODELING_EDITOR_MIN_HEIGHT, MODELING_EDITOR_MIN_WIDTH } from 'app/shared/constants/modeling.constants';
import interact from 'interactjs';

@Component({ template: '' })
export abstract class ModelingComponent {
    @ViewChild('editorContainer', { static: false }) editorContainer: ElementRef;
    @ViewChild('resizeContainer', { static: false }) resizeContainer: ElementRef;
    @Input() resizeOptions: { horizontalResize?: boolean; verticalResize?: boolean };
    @Input() umlModel: UMLModel;
    @Input() diagramType?: UMLDiagramType;
    @Input() explanation: string;
    @Input() readOnly = false;

    apollonEditor?: ApollonEditor;

    // Icons
    faGripLines = faGripLines;
    faGripLinesVertical = faGripLinesVertical;

    protected setupInteract(): void {
        if (this.resizeOptions) {
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: this.resizeOptions.horizontalResize && '.draggable-right', bottom: this.resizeOptions.verticalResize, top: false },
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
                    if (this.resizeOptions.horizontalResize) {
                        target.style.width = event.rect.width + 'px';
                    }
                    if (this.resizeOptions.verticalResize) {
                        target.style.height = event.rect.height + 'px';
                    }
                });
        }
    }
}
