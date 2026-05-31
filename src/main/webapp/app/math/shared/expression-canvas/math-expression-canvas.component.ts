import { Component, computed, input, output, signal } from '@angular/core';
import { MathNode } from '../entities/math-node.model';
import { BlockDefinitionModel } from '../entities/block-definition.model';
import { MathNodeComponent, MathNodeContext } from '../../manage/update/math-math-node/math-math-node.component';

@Component({
    selector: 'jhi-math-expression-canvas',
    templateUrl: './math-expression-canvas.component.html',
    imports: [MathNodeComponent],
})
export class MathExpressionCanvasComponent {
    rootNodes = input.required<MathNode[]>();
    draggingPayload = input<string | undefined>(undefined);
    nodeCtx = input.required<MathNodeContext>();
    blocks = input<BlockDefinitionModel[]>([]);
    readonly = input<boolean>(false);

    gapDrop = output<{ gapIndex: number; payload: string }>();

    dragHoveredGapIndex = signal<number | undefined>(undefined);

    gaps = computed(() => Array.from({ length: this.rootNodes().length + 1 }, (_, i) => ({ gapIndex: i })));

    onGapDragOver(event: DragEvent, gapIndex: number): void {
        event.preventDefault();
        event.dataTransfer!.dropEffect = 'copy';
        this.dragHoveredGapIndex.set(gapIndex);
    }

    onGapDragLeave(event: DragEvent): void {
        if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node)) {
            this.dragHoveredGapIndex.set(undefined);
        }
    }

    onGapDrop(event: DragEvent, gapIndex: number): void {
        event.preventDefault();
        const payload = event.dataTransfer!.getData('text/plain') || this.draggingPayload();
        if (!payload) return;
        this.dragHoveredGapIndex.set(undefined);
        this.gapDrop.emit({ gapIndex, payload });
    }
}
