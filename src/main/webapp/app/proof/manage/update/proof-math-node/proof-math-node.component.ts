import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TagModule } from 'primeng/tag';

import { ProofBlockRegistryService } from '../../service/proof-block-registry.service';
import { MathNode } from '../../../shared/entities/math-node.model';

export interface MathNodeContext {
    draggingBlockType: string | undefined;
    editable: boolean;
    selectFn: (rootIndex: number, path: number[]) => void;
    dropFn: (rootIndex: number, path: number[], payload: string) => void;
    valueChangeFn: (rootIndex: number, path: number[], value: string) => void;
    removeFn: (rootIndex: number, path: number[]) => void;
    isSelectedFn: (rootIndex: number, path: number[]) => boolean;
}

@Component({
    selector: 'jhi-math-node',
    templateUrl: './proof-math-node.component.html',
    styleUrl: './proof-math-node.component.scss',
    imports: [FormsModule, TagModule, MathNodeComponent],
})
export class MathNodeComponent {
    node = input.required<MathNode>();
    path = input<number[]>([]);
    rootIndex = input<number>(0);
    ctx = input.required<MathNodeContext>();

    private registry = inject(ProofBlockRegistryService);

    descriptor = computed(() => this.registry.descriptorFor(this.node().type));
    layoutCategory = computed(() => this.descriptor()?.layoutCategory ?? 'UNKNOWN');
    displaySymbol = computed(() => this.descriptor()?.displaySymbol ?? '?');

    isHovered = signal(false);
    isSelected = computed(() => this.ctx().isSelectedFn(this.rootIndex(), this.path()));
    isDraggingActive = computed(() => this.ctx().draggingBlockType !== undefined);

    slotChild(slotKey: string): MathNode | undefined {
        return this.node().slots?.[slotKey]?.[0];
    }

    slotChildPath(slotKey: string): number[] {
        const node = this.node();
        if (!node.slots) return this.path();
        const sortedKeys = Object.keys(node.slots).sort();
        let offset = 0;
        for (const key of sortedKeys) {
            if (key === slotKey) {
                return [...this.path(), offset];
            }
            offset += node.slots[key]?.length ?? 0;
        }
        return this.path();
    }

    unknownChildren(): { child: MathNode; childPath: number[] }[] {
        const node = this.node();
        if (!node.slots) return [];
        const sortedKeys = Object.keys(node.slots).sort();
        let offset = 0;
        const result: { child: MathNode; childPath: number[] }[] = [];
        for (const key of sortedKeys) {
            for (const child of node.slots[key]) {
                result.push({ child, childPath: [...this.path(), offset] });
                offset++;
            }
        }
        return result;
    }

    needsParens(child: MathNode, slotKey: string): boolean {
        const parentDesc = this.descriptor();
        if (!parentDesc) return false;
        const childDesc = this.registry.descriptorFor(child.type);
        const parentPrec = parentDesc.precedence ?? 0;
        const childPrec = childDesc?.precedence ?? -Infinity;
        if (childPrec === -Infinity) return true;
        // Unary-prefix: child binds tighter than the operator if and only if its precedence is strictly higher.
        if (parentDesc.layoutCategory === 'UNARY_PREFIX') {
            return childPrec < parentPrec;
        }
        if (parentDesc.associativity === 'NONE') return false;
        return slotKey === 'right' ? childPrec <= parentPrec : childPrec < parentPrec;
    }

    handleClick(event: MouseEvent): void {
        event.stopPropagation();
        this.ctx().selectFn(this.rootIndex(), this.path());
    }

    handleDragOver(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        event.dataTransfer!.dropEffect = 'copy';
        this.isHovered.set(true);
    }

    handleDragLeave(event: DragEvent): void {
        if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node)) {
            this.isHovered.set(false);
        }
    }

    handleDrop(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        const blockType = event.dataTransfer!.getData('text/plain') || this.ctx().draggingBlockType;
        if (!blockType) return;
        this.isHovered.set(false);
        this.ctx().dropFn(this.rootIndex(), this.path(), blockType);
    }

    handleValueInput(event: Event): void {
        this.ctx().valueChangeFn(this.rootIndex(), this.path(), (event.target as HTMLInputElement).value);
    }

    handleRemove(event: MouseEvent): void {
        event.stopPropagation();
        this.ctx().removeFn(this.rootIndex(), this.path());
    }

    stopEvent(event: Event): void {
        event.stopPropagation();
    }
}
