import { Component, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { MathNode } from '../../../shared/entities/math-node.model';
import { BlockDefinitionModel } from '../../../shared/entities/block-definition.model';
import { MathNodeLatexPipe } from '../../../shared/math-node-latex.pipe';
import { ProofBlockRegistryService } from '../../service/proof-block-registry.service';
import { MathNodeContext } from '../proof-math-node/proof-math-node.component';
import { ProofExpressionCanvasComponent } from '../../../shared/expression-canvas/proof-expression-canvas.component';

@Component({
    selector: 'jhi-proof-builder',
    templateUrl: './proof-builder.component.html',
    imports: [FormsModule, TranslateDirective, ProofExpressionCanvasComponent, MathNodeLatexPipe],
})
export class ProofBuilderComponent implements OnInit {
    private blockRegistryService = inject(ProofBlockRegistryService);

    expression = input<MathNode | undefined>(undefined);
    expressionChange = output<MathNode | undefined>();
    label = input<string>('Expression');

    blocks = signal<BlockDefinitionModel[]>([]);
    rootNodes = signal<MathNode[]>([]);

    selectedBlockType = signal<string>('number');
    terminalValue = signal<string>('');
    selectedRootIndex = signal<number | undefined>(undefined);
    selectedPath = signal<number[]>([]);
    errorMessage = signal<string | undefined>(undefined);

    draggingBlockType = signal<string | undefined>(undefined);

    nodeCtx = computed<MathNodeContext>(() => ({
        draggingBlockType: this.draggingBlockType(),
        editable: true,
        selectFn: (ri, p) => this.selectNode(ri, p),
        dropFn: (ri, p, bt) => this.handleNodeDrop(ri, p, bt),
        valueChangeFn: (ri, p, v) => this.updateNodeValue(ri, p, v),
        removeFn: (ri, p) => this.removeNode(ri, p),
        isSelectedFn: (ri, p) => this.isSelectedNode(ri, p),
    }));

    constructor() {
        effect(() => {
            const expr = this.expression();
            const currentRoot = this.rootNodes()[0];
            if (expr !== currentRoot) {
                this.rootNodes.set(expr ? [expr] : []);
            }
        });
    }

    ngOnInit(): void {
        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.blocks.set(blocks),
        });
    }

    get selectedBlock(): BlockDefinitionModel | undefined {
        return this.blocks().find((b) => b.type === this.selectedBlockType());
    }

    get isTerminal(): boolean {
        const block = this.selectedBlock;
        return !block || !block.slots?.length;
    }

    // ── Selection ──────────────────────────────────────────────────────────────

    selectNode(rootIndex: number, path: number[]): void {
        this.selectedRootIndex.set(rootIndex);
        this.selectedPath.set(path);
    }

    isSelectedNode(rootIndex: number, path: number[]): boolean {
        if (this.selectedRootIndex() !== rootIndex) return false;
        const sel = this.selectedPath();
        return sel.length === path.length && sel.every((v, i) => v === path[i]);
    }

    // ── Node drop (from MathNodeComponent) ────────────────────────────────────

    handleNodeDrop(rootIndex: number, path: number[], blockType: string): void {
        const newNode = this.buildNode(blockType);
        const root = this.rootNodes()[rootIndex];
        if (!root) return;
        if (path.length === 0) {
            this.rootNodes.update((list) => list.map((r, i) => (i === rootIndex ? newNode : r)));
        } else {
            try {
                const updated = this.replaceAtPath(root, path, newNode);
                this.rootNodes.update((list) => list.map((r, i) => (i === rootIndex ? updated : r)));
            } catch {
                /* path mismatch — ignore */
            }
        }
        this.draggingBlockType.set(undefined);
        this.emitChange();
    }

    // ── Value editing ──────────────────────────────────────────────────────────

    updateNodeValue(rootIndex: number, path: number[], value: string): void {
        const root = this.rootNodes()[rootIndex];
        if (!root) return;
        const updated = this.setValueAtPath(root, path, value);
        this.rootNodes.update((list) => list.map((r, i) => (i === rootIndex ? updated : r)));
        this.emitChange();
    }

    removeNode(rootIndex: number, path: number[]): void {
        if (path.length === 0) {
            this.rootNodes.update((list) => list.filter((_, i) => i !== rootIndex));
            if (this.selectedRootIndex() === rootIndex) {
                this.selectedRootIndex.set(undefined);
                this.selectedPath.set([]);
            }
        } else {
            const root = this.rootNodes()[rootIndex];
            if (!root) return;
            try {
                const updated = this.replaceAtPath(root, path, { type: 'number', value: '?' });
                this.rootNodes.update((list) => list.map((r, i) => (i === rootIndex ? updated : r)));
            } catch {
                /* ignore */
            }
        }
        this.emitChange();
    }

    // ── Keyboard fallback ──────────────────────────────────────────────────────

    insertAtSelected(): void {
        this.errorMessage.set(undefined);
        const newNode = this.buildNode(this.selectedBlockType(), this.isTerminal ? this.terminalValue() || '?' : undefined);
        const rootIdx = this.selectedRootIndex();

        if (rootIdx === undefined) {
            this.rootNodes.update((list) => [...list, newNode]);
        } else {
            const root = this.rootNodes()[rootIdx];
            if (!root) return;
            if (this.selectedPath().length === 0) {
                this.rootNodes.update((list) => list.map((r, i) => (i === rootIdx ? newNode : r)));
            } else {
                try {
                    const updated = this.replaceAtPath(root, this.selectedPath(), newNode);
                    this.rootNodes.update((list) => list.map((r, i) => (i === rootIdx ? updated : r)));
                } catch {
                    this.errorMessage.set('Invalid selection path');
                    return;
                }
            }
        }
        this.emitChange();
    }

    clearExpression(): void {
        this.rootNodes.set([]);
        this.selectedRootIndex.set(undefined);
        this.selectedPath.set([]);
        this.expressionChange.emit(undefined);
    }

    // ── Palette DnD ───────────────────────────────────────────────────────────

    onBlockDragStart(event: DragEvent, blockType: string): void {
        this.draggingBlockType.set(blockType);
        event.dataTransfer!.setData('text/plain', blockType);
        event.dataTransfer!.effectAllowed = 'copy';
    }

    onBlockDragEnd(): void {
        this.draggingBlockType.set(undefined);
    }

    // ── Gap zone DnD ──────────────────────────────────────────────────────────

    handleGapDrop(event: { gapIndex: number; payload: string }): void {
        const { gapIndex, payload: blockType } = event;
        const roots = this.rootNodes();
        const block = this.blocks().find((b) => b.type === blockType);

        const isMergeGap = block && (block.slots?.length ?? 0) >= 2 && gapIndex > 0 && gapIndex < roots.length;
        if (isMergeGap) {
            const left = roots[gapIndex - 1];
            const right = roots[gapIndex];
            const sortedSlots = [...block!.slots!].sort();
            const slots: Record<string, MathNode[]> = {};
            for (const slot of block!.slots!) slots[slot] = [{ type: 'number', value: '?' }];
            slots[sortedSlots[0]] = [left];
            slots[sortedSlots[1]] = [right];
            const merged: MathNode = { type: blockType, slots };
            this.rootNodes.update((list) => [...list.slice(0, gapIndex - 1), merged, ...list.slice(gapIndex + 1)]);
        } else {
            const newNode = this.buildNode(blockType);
            this.rootNodes.update((list) => [...list.slice(0, gapIndex), newNode, ...list.slice(gapIndex)]);
        }
        this.draggingBlockType.set(undefined);
        this.emitChange();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private buildNode(blockType: string, value?: string): MathNode {
        const block = this.blocks().find((b) => b.type === blockType);
        if (!block || !block.slots?.length) {
            return { type: blockType, value: value ?? '?' };
        }
        const slots: Record<string, MathNode[]> = {};
        for (const slot of block.slots) {
            slots[slot] = [{ type: 'number', value: '?' }];
        }
        return { type: blockType, slots };
    }

    private emitChange(): void {
        const nodes = this.rootNodes();
        this.expressionChange.emit(nodes.length > 0 ? nodes[0] : undefined);
    }

    private replaceAtPath(root: MathNode, path: number[], replacement: MathNode): MathNode {
        if (path.length === 0) return replacement;
        const [head, ...tail] = path;
        if (!root.slots) throw new Error('Cannot navigate into terminal node');
        const sortedKeys = Object.keys(root.slots).sort();
        let globalIndex = 0;
        const newSlots: Record<string, MathNode[]> = {};
        for (const key of sortedKeys) {
            const children = root.slots[key];
            newSlots[key] = children.map((child) => {
                const replaced = globalIndex === head ? this.replaceAtPath(child, tail, replacement) : child;
                globalIndex++;
                return replaced;
            });
        }
        return { type: root.type, value: root.value, slots: newSlots };
    }

    private setValueAtPath(root: MathNode, path: number[], value: string): MathNode {
        if (path.length === 0) return { type: root.type, value, slots: root.slots };
        const [head, ...tail] = path;
        if (!root.slots) throw new Error('Cannot navigate into terminal node');
        const sortedKeys = Object.keys(root.slots).sort();
        let globalIndex = 0;
        const newSlots: Record<string, MathNode[]> = {};
        for (const key of sortedKeys) {
            const children = root.slots[key];
            newSlots[key] = children.map((child) => {
                const updated = globalIndex === head ? this.setValueAtPath(child, tail, value) : child;
                globalIndex++;
                return updated;
            });
        }
        return { type: root.type, value: root.value, slots: newSlots };
    }
}
