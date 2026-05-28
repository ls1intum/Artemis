import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MathNode, applyRule, distance, equalsAC, isTautology, normalizeAC } from '../../../shared/entities/math-node.model';
import { DerivationStep } from '../../../shared/entities/derivation-step.model';
import { BlockDefinitionModel, RewriteRuleModel } from '../../../shared/entities/block-definition.model';
import { StepDirection } from '../../../shared/entities/rule-direction.model';
import { GoalMode } from '../../../shared/entities/goal-mode.model';
import { MathNodeLatexPipe } from '../../../shared/math-node-latex.pipe';
import { KatexStringPipe } from '../../../shared/katex-string.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProofBlockRegistryService } from '../../service/proof-block-registry.service';
import { ProofExpressionCanvasComponent } from '../../../shared/expression-canvas/proof-expression-canvas.component';
import { MathNodeContext } from '../proof-math-node/proof-math-node.component';

@Component({
    selector: 'jhi-proof-derivation-workspace',
    templateUrl: './proof-derivation-workspace.component.html',
    imports: [FormsModule, MathNodeLatexPipe, KatexStringPipe, ArtemisTranslatePipe, ProofExpressionCanvasComponent],
})
export class ProofDerivationWorkspaceComponent implements OnInit {
    private blockRegistryService = inject(ProofBlockRegistryService);

    sourceExpression = input<MathNode | undefined>(undefined);
    targetExpression = input<MathNode | undefined>(undefined);
    /** When EQUATION, the workspace starts from {@link goalExpression} and completes on tautology. Defaults to TRANSFORMATION. */
    goalMode = input<GoalMode>('TRANSFORMATION');
    goalExpression = input<MathNode | undefined>(undefined);
    /** When true, equality + tautology + no-regress comparisons treat {@code +} and {@code ·} as commutative/associative. */
    acNormalization = input<boolean>(false);
    initialSteps = input<DerivationStep[]>([]);
    onlyShowApplicableRules = input<boolean>(false);
    stepsChange = output<DerivationStep[]>();

    currentExpression = signal<MathNode | undefined>(undefined);
    steps = signal<DerivationStep[]>([]);
    blocks = signal<BlockDefinitionModel[]>([]);
    selectedRuleId = signal<string>('');
    selectedDirection = signal<StepDirection>('FORWARD');
    selectedNodePath = signal<number[] | undefined>(undefined);
    ruleApplicationError = signal<string | undefined>(undefined);
    draggingPayload = signal<string | undefined>(undefined);
    ruleSearch = signal('');

    selectedRule = computed<RewriteRuleModel | undefined>(() => this.allRules.find((r) => r.id === this.selectedRuleId()));

    filteredBlocks = computed<BlockDefinitionModel[]>(() => {
        const filterApplicable = this.onlyShowApplicableRules() && this.selectedNodePath() !== undefined;
        const applicable = filterApplicable ? this.applicableRuleIds() : undefined;

        const raw = this.ruleSearch().trim().toLowerCase();
        const compact = raw.replace(/\s+/g, '');

        return this.blocks()
            .map((block) => ({
                ...block,
                rules: (block.rules ?? []).filter((r) => {
                    if (applicable && !applicable.has(r.id)) return false;
                    if (!raw) return true;
                    if (r.name.toLowerCase().includes(raw)) return true;
                    const latex = (r.paletteLatex ?? '').toLowerCase().replace(/\s+/g, '');
                    return latex.includes(compact);
                }),
            }))
            .filter((block) => block.rules.length > 0);
    });

    rootNodes = computed<MathNode[]>(() => {
        const e = this.currentExpression();
        return e ? [e] : [];
    });

    isComplete = computed(() => {
        const cur = this.currentExpression();
        if (!cur) return false;
        const ac = this.acNormalization();
        if (this.goalMode() === 'EQUATION') {
            const norm = canonical(cur, ac);
            return !!norm && isTautology(norm);
        }
        const tgt = this.targetExpression();
        return !!tgt && equalsAC(cur, tgt, ac);
    });

    startExpression = computed<MathNode | undefined>(() => (this.goalMode() === 'EQUATION' ? this.goalExpression() : this.sourceExpression()));

    /** Set of result trees the student has visited so far. Used by the no-regress UX. */
    private visitedStates = computed<Set<string>>(() => {
        const ac = this.acNormalization();
        const start = this.startExpression();
        const visited = new Set<string>();
        if (start) visited.add(JSON.stringify(canonical(start, ac)));
        for (const step of this.steps()) {
            visited.add(JSON.stringify(canonical(step.resultExpression, ac)));
        }
        return visited;
    });

    /** Progress toward the goal in [0, 1], or undefined while we can't compute it (no goal yet). */
    progress = computed<number | undefined>(() => {
        const cur = this.currentExpression();
        if (!cur) return undefined;
        const ac = this.acNormalization();
        if (this.goalMode() === 'EQUATION') {
            const goal = this.goalExpression();
            if (!goal) return undefined;
            return computeProgressEquation(goal, cur, ac);
        }
        const src = this.sourceExpression();
        const tgt = this.targetExpression();
        if (!src || !tgt) return undefined;
        return computeProgressTransformation(src, tgt, cur, ac);
    });

    applicableRuleIds = computed<Set<string>>(() => {
        const path = this.selectedNodePath();
        const current = this.currentExpression();
        if (path === undefined || !current) return new Set<string>();
        const ac = this.acNormalization();
        const visited = this.visitedStates();
        const fresh = (tree: MathNode | undefined) => tree !== undefined && !visited.has(JSON.stringify(canonical(tree, ac)));
        const applicable = new Set<string>();
        for (const block of this.blocks()) {
            for (const rule of block.rules ?? []) {
                // Rule is applicable if either direction succeeds AND the result is not a previously visited state.
                const forward = applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], 'FORWARD', rule.direction);
                const reverse =
                    rule.direction === 'BIDIRECTIONAL' ? applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], 'REVERSE', rule.direction) : undefined;
                if (fresh(forward) || fresh(reverse)) {
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
                const current = this.currentExpression();
                if (current) this.selectedDirection.set(this.pickDirection(rule, current, path));
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
            this.currentExpression.set(this.startExpression());
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
        if (this.isComplete()) return;
        this.selectedRuleId.set(ruleId);
        const rule = this.allRules.find((r) => r.id === ruleId);
        const cur = this.currentExpression();
        const path = this.selectedNodePath();
        if (rule && cur && path !== undefined) {
            this.selectedDirection.set(this.pickDirection(rule, cur, path));
        } else {
            this.selectedDirection.set('FORWARD');
        }
        this.ruleApplicationError.set(undefined);
        if (this.selectedNodePath() !== undefined) {
            this.applySelectedRule();
        }
    }

    toggleDirection(): void {
        if (this.selectedRule()?.direction !== 'BIDIRECTIONAL') return;
        this.selectedDirection.update((d) => (d === 'FORWARD' ? 'REVERSE' : 'FORWARD'));
        if (this.selectedNodePath() !== undefined) {
            this.applySelectedRule();
        }
    }

    selectNode(path: number[]): void {
        if (this.isComplete()) return;
        this.selectedNodePath.set(path);
        this.ruleApplicationError.set(undefined);
        if (this.selectedRuleId()) {
            const rule = this.allRules.find((r) => r.id === this.selectedRuleId());
            const cur = this.currentExpression();
            if (rule && cur) this.selectedDirection.set(this.pickDirection(rule, cur, path));
            this.applySelectedRule();
        }
    }

    /**
     * Picks the application direction that actually matches at `path` for `rule` against `current`, preferring FORWARD when
     * both sides match. Falls back to FORWARD when neither matches so the regular "Rule does not apply" error surfaces.
     */
    private pickDirection(rule: RewriteRuleModel, current: MathNode, path: number[]): StepDirection {
        if (applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], 'FORWARD', rule.direction)) {
            return 'FORWARD';
        }
        if (rule.direction === 'BIDIRECTIONAL' && applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], 'REVERSE', rule.direction)) {
            return 'REVERSE';
        }
        return 'FORWARD';
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
        const direction = this.selectedDirection();
        const newTree = applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], direction, rule.direction);
        if (!newTree) {
            this.ruleApplicationError.set('Rule does not apply at the selected node.');
            return;
        }
        if (this.visitedStates().has(JSON.stringify(canonical(newTree, this.acNormalization())))) {
            this.ruleApplicationError.set('This step would return to a previously visited state.');
            return;
        }
        const newStep: DerivationStep = {
            stepIndex: this.steps().length,
            appliedRuleId: ruleId,
            targetNodePath: path,
            resultExpression: newTree,
            direction,
        };
        this.steps.update((s) => [...s, newStep]);
        this.currentExpression.set(newTree);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.selectedDirection.set('FORWARD');
        this.stepsChange.emit(this.steps());
    }

    undoLastStep(): void {
        this.steps.update((s) => {
            const updated = s.slice(0, -1);
            const expr = updated.length > 0 ? updated[updated.length - 1].resultExpression : this.startExpression();
            this.currentExpression.set(expr);
            return updated;
        });
        this.ruleApplicationError.set(undefined);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.stepsChange.emit(this.steps());
    }

    onRuleDragStart(event: DragEvent, ruleId: string): void {
        if (this.isComplete()) {
            event.preventDefault();
            return;
        }
        this.draggingPayload.set(ruleId);
        event.dataTransfer!.setData('text/plain', ruleId);
        event.dataTransfer!.effectAllowed = 'copy';
    }

    onRuleDragEnd(): void {
        this.draggingPayload.set(undefined);
    }
}

/** Returns the AC-normalised form when ac is true, the input unchanged otherwise. */
function canonical(node: MathNode | undefined, ac: boolean): MathNode | undefined {
    return ac ? normalizeAC(node) : node;
}

/** Progress in [0, 1] toward the target — how much of the initial source→target distance has been closed. */
function computeProgressTransformation(source: MathNode, target: MathNode, current: MathNode, ac: boolean): number {
    const initial = distance(canonical(source, ac), canonical(target, ac));
    if (initial <= 0) return 1;
    const remaining = distance(canonical(current, ac), canonical(target, ac));
    return Math.max(0, Math.min(1, 1 - remaining / initial));
}

/** Progress in [0, 1] toward tautology — how much of the initial side-distance has been closed. */
function computeProgressEquation(goal: MathNode, current: MathNode, ac: boolean): number {
    const initialDist = sideDistance(goal, ac);
    if (initialDist <= 0) return 1;
    const remaining = sideDistance(current, ac);
    return Math.max(0, Math.min(1, 1 - remaining / initialDist));
}

function sideDistance(node: MathNode, ac: boolean): number {
    if (node.type !== 'equality' || !node.slots) return 0;
    const left = node.slots['left']?.[0];
    const right = node.slots['right']?.[0];
    if (!left || !right) return 0;
    return distance(canonical(left, ac), canonical(right, ac));
}
