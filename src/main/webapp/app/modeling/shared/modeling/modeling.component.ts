import { Component, ElementRef, computed, input, model, viewChild } from '@angular/core';
import { faGripLines, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon/external';
import { MODELING_EDITOR_MAX_HEIGHT, MODELING_EDITOR_MAX_WIDTH, MODELING_EDITOR_MIN_HEIGHT, MODELING_EDITOR_MIN_WIDTH } from 'app/foundation/constants/modeling.constants';
import { ResizableEdges } from 'app/shared-ui/directives/resizable.directive';

@Component({
    template: '',
})
export abstract class ModelingComponent {
    protected readonly faGripLines = faGripLines;
    protected readonly faGripLinesVertical = faGripLinesVertical;

    // Size constraints (px) for the resizable editor container, exposed to the templates.
    protected readonly MODELING_EDITOR_MIN_WIDTH = MODELING_EDITOR_MIN_WIDTH;
    protected readonly MODELING_EDITOR_MAX_WIDTH = MODELING_EDITOR_MAX_WIDTH;
    protected readonly MODELING_EDITOR_MIN_HEIGHT = MODELING_EDITOR_MIN_HEIGHT;
    protected readonly MODELING_EDITOR_MAX_HEIGHT = MODELING_EDITOR_MAX_HEIGHT;

    readonly editorContainer = viewChild<ElementRef<HTMLElement>>('editorContainer');
    readonly resizeContainer = viewChild<ElementRef<HTMLElement>>('resizeContainer');
    resizeOptions = input<{
        horizontalResize?: boolean;
        verticalResize?: boolean;
    }>();
    umlModel = input<UMLModel>();
    diagramType = input<UMLDiagramType>();
    explanation = model<string>('');
    readOnly = input(false);

    apollonEditor?: ApollonEditor;

    /** Which edges of the resize container can be dragged, derived from {@link resizeOptions}. */
    protected readonly resizableEdges = computed<ResizableEdges>(() => {
        const resizeOptions = this.resizeOptions();
        return {
            right: resizeOptions?.horizontalResize ? '.draggable-right' : undefined,
            bottom: resizeOptions?.verticalResize ? '.draggable-bottom' : undefined,
        };
    });
}
