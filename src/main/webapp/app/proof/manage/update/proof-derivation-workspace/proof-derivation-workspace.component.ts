import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { MathNode, applyRule, mathNodesEqual } from '../../../shared/entities/math-node.model';
import { DerivationStep } from '../../../shared/entities/derivation-step.model';
import { BlockDefinitionModel, RewriteRuleModel } from '../../../shared/entities/block-definition.model';
import { MathNodeLatexPipe } from '../../../shared/math-node-latex.pipe';
import { KatexStringPipe } from '../../../shared/katex-string.pipe';
import { ProofBlockRegistryService } from '../../service/proof-block-registry.service';
import { ProofExpressionCanvasComponent } from '../../../shared/expression-canvas/proof-expression-canvas.component';
import { MathNodeContext } from '../proof-math-node/proof-math-node.component';

@Component({
    selector: 'jhi-proof-derivation-workspace',
    templateUrl: './proof-derivation-workspace.component.html',
    imports: [MathNodeLatexPipe, KatexStringPipe, ProofExpressionCanvasComponent],
})
export class ProofDerivationWorkspaceComponent implements OnInit {
    private blockRegistryService = inject(ProofBlockRegistryService);

    sourceExpression = input<MathNode | undefined>(undefined);
    targetExpression = input<MathNode | undefined>(undefined);
    initialSteps = input<DerivationStep[]>([]);
    stepsChange = output<DerivationStep[]>();

    currentExpression = signal<MathNode | undefined>(undefined);
    steps = signal<DerivationStep[]>([]);
    blocks = signal<BlockDefinitionModel[]>([]);
    selectedRuleId = signal<string>('');
    selectedNodePath = signal<number[] | undefined>(undefined);
    ruleApplicationError = signal<string | undefined>(undefined);
    draggingPayload = signal<string | undefined>(undefined);

    rootNodes = computed<MathNode[]>(() => {
        const e = this.currentExpression();
        return e ? [e] : [];
    });

    isComplete = computed(() => {
        const cur = this.currentExpression();
        const tgt = this.targetExpression();
        if (!cur || !tgt) return false;
        return mathNodesEqual(cur, tgt);
    });

    applicableRuleIds = computed<Set<string>>(() => {
        const path = this.selectedNodePath();
        const current = this.currentExpression();
        if (path === undefined || !current) return new Set<string>();
        const applicable = new Set<string>();
        for (const block of this.blocks()) {
            for (const rule of block.rules ?? []) {
                if (applyRule(current, path, rule.pattern, rule.template) !== undefined) {
                    applicable.add(rule.id);
                }
            }
        }
        return applicable;
    });

    nodeCtx = computed<MathNodeContext>(() => ({
        draggingBlockType: this.draggingPayload(),
        editable: false,
        selectFn: (_, path) => this.selectNode(path),
        dropFn: (_, path, payload) => {
            const rule = this.allRules.find((r) => r.id === payload);
            if (rule) {
                this.selectedNodePath.set(path);
                this.selectedRuleId.set(payload);
                this.applySelectedRule();
            }
        },
        valueChangeFn: () => {},
        removeFn: () => {},
        isSelectedFn: (_, path) => this.isSelectedPath(path),
    }));

    ngOnInit(): void {
        const initSteps = this.initialSteps();
        if (initSteps.length > 0) {
            this.steps.set(initSteps);
            this.currentExpression.set(initSteps[initSteps.length - 1].resultExpression);
        } else {
            this.currentExpression.set(this.sourceExpression());
        }
        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.blocks.set(blocks),
        });
    }

    get allRules(): RewriteRuleModel[] {
        return this.blocks().flatMap((b) => b.rules ?? []);
    }

    get selectedRuleName(): string {
        return this.allRules.find((r) => r.id === this.selectedRuleId())?.name ?? this.selectedRuleId();
    }

    ruleNameFor(ruleId: string): string {
        return this.allRules.find((r) => r.id === ruleId)?.name ?? ruleId;
    }

    isSelectedPath(path: number[]): boolean {
        const sel = this.selectedNodePath();
        if (sel === undefined) return false;
        return sel.length === path.length && sel.every((v, i) => v === path[i]);
    }

    selectRule(ruleId: string): void {
        this.selectedRuleId.set(ruleId);
        this.ruleApplicationError.set(undefined);
        if (this.selectedNodePath() !== undefined) {
            this.applySelectedRule();
        }
    }

    selectNode(path: number[]): void {
        this.selectedNodePath.set(path);
        this.ruleApplicationError.set(undefined);
        if (this.selectedRuleId()) {
            this.applySelectedRule();
        }
    }

    applySelectedRule(): void {
        this.ruleApplicationError.set(undefined);
        const ruleId = this.selectedRuleId();
        const rule = this.allRules.find((r) => r.id === ruleId);
        if (!rule) {
            this.ruleApplicationError.set('Select a rule first.');
            return;
        }
        const current = this.currentExpression();
        if (!current) {
            this.ruleApplicationError.set('No current expression.');
            return;
        }
        const path = this.selectedNodePath() ?? [];
        const newTree = applyRule(current, path, rule.pattern, rule.template);
        if (!newTree) {
            this.ruleApplicationError.set('Rule does not apply at the selected node.');
            return;
        }
        const newStep: DerivationStep = {
            stepIndex: this.steps().length,
            appliedRuleId: ruleId,
            targetNodePath: path,
            resultExpression: newTree,
        };
        this.steps.update((s) => [...s, newStep]);
        this.currentExpression.set(newTree);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.stepsChange.emit(this.steps());
    }

    undoLastStep(): void {
        this.steps.update((s) => {
            const updated = s.slice(0, -1);
            const expr = updated.length > 0 ? updated[updated.length - 1].resultExpression : this.sourceExpression();
            this.currentExpression.set(expr);
            return updated;
        });
        this.ruleApplicationError.set(undefined);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.stepsChange.emit(this.steps());
    }

    onRuleDragStart(event: DragEvent, ruleId: string): void {
        this.draggingPayload.set(ruleId);
        event.dataTransfer!.setData('text/plain', ruleId);
        event.dataTransfer!.effectAllowed = 'copy';
    }

    onRuleDragEnd(): void {
        this.draggingPayload.set(undefined);
    }
}
