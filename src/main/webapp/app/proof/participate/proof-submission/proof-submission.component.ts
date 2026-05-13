import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { DerivationStep } from 'app/proof/shared/entities/derivation-step.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ExerciseSubmitButtonComponent } from 'app/exercise/shared/exercise-submit-button/exercise-submit-button.component';
import { MathNodeLatexPipe } from 'app/proof/shared/math-node-latex.pipe';
import { KatexStringPipe } from 'app/proof/shared/katex-string.pipe';
import { MathNode, applyRule, isTautology, mathNodesEqual } from 'app/proof/shared/entities/math-node.model';
import { BlockDefinitionModel, RewriteRuleModel } from 'app/proof/shared/entities/block-definition.model';
import { ProofBlockRegistryService } from 'app/proof/manage/service/proof-block-registry.service';
import { MathNodeContext } from 'app/proof/manage/update/proof-math-node/proof-math-node.component';
import { ProofExpressionCanvasComponent } from 'app/proof/shared/expression-canvas/proof-expression-canvas.component';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';

type StepStatus = 'pending' | 'valid' | 'invalid';

@Component({
    selector: 'jhi-proof-submission',
    templateUrl: './proof-submission.component.html',
    imports: [
        HeaderParticipationPageComponent,
        ButtonComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        HtmlForMarkdownPipe,
        ExerciseSubmitButtonComponent,
        MathNodeLatexPipe,
        KatexStringPipe,
        ProofExpressionCanvasComponent,
    ],
})
export class ProofSubmissionComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private proofSubmissionService = inject(ProofSubmissionService);
    private blockRegistryService = inject(ProofBlockRegistryService);
    private alertService = inject(AlertService);

    participationId = input<number>();

    proofExercise: ProofExercise;
    participation: StudentParticipation;
    submission: ProofSubmission;
    result?: Result;

    isSaving = false;

    // Derivation state
    currentExpression = signal<MathNode | undefined>(undefined);
    steps = signal<DerivationStep[]>([]);
    blocks = signal<BlockDefinitionModel[]>([]);
    selectedRuleId = signal<string>('');
    selectedNodePath = signal<number[] | undefined>(undefined);
    ruleApplicationError = signal<string | undefined>(undefined);
    draggingPayload = signal<string | undefined>(undefined);

    // Verification state
    stepStatuses = signal<StepStatus[]>([]);
    stepErrors = signal<(string | undefined)[]>([]);

    // Autosave state
    hasUnsavedChanges = signal(false);
    lastSavedAt = signal<Date | undefined>(undefined);
    private autosaveInterval: ReturnType<typeof setInterval> | undefined;
    private autosaveTick = 0;

    submissionRootNodes = computed<MathNode[]>(() => {
        const e = this.currentExpression();
        return e ? [e] : [];
    });

    isComplete = computed(() => {
        const current = this.currentExpression();
        const target = this.proofExercise?.targetExpression;
        if (!current || !target) return false;
        return mathNodesEqual(current, target) || isTautology(current);
    });

    /** Rule IDs that match at the currently selected node path. Empty set when no node is selected. */
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

    submissionNodeCtx = computed<MathNodeContext>(() => ({
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

    ngOnInit() {
        const participationIdParam = this.participationId() !== undefined ? this.participationId() : Number(this.route.snapshot.paramMap.get('participationId'));
        if (participationIdParam === undefined || Number.isNaN(participationIdParam)) {
            return this.alertService.error('artemisApp.proofExercise.error');
        }
        const participationId = participationIdParam!;

        this.proofSubmissionService.getDataForProofEditor(participationId).subscribe({
            next: (response) => {
                this.submission = response.body as ProofSubmission;
                this.participation = this.submission.participation as StudentParticipation;
                this.proofExercise = this.participation.exercise as ProofExercise;

                const results = this.submission.results;
                if (results && results.length > 0) {
                    this.result = results[results.length - 1];
                }

                if (this.submission.steps && this.submission.steps.length > 0) {
                    this.steps.set(this.submission.steps);
                    const lastStep = this.submission.steps[this.submission.steps.length - 1];
                    this.currentExpression.set(lastStep.resultExpression);
                } else {
                    this.currentExpression.set(this.proofExercise.sourceExpression);
                }
            },
            error: () => this.alertService.error('artemisApp.proofExercise.error'),
        });

        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.blocks.set(blocks),
        });

        this.autosaveInterval = setInterval(() => {
            this.autosaveTick++;
            if (this.autosaveTick >= AUTOSAVE_EXERCISE_INTERVAL && this.hasUnsavedChanges() && !this.submission?.submitted) {
                this.autosaveTick = 0;
                this.save(true);
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    ngOnDestroy() {
        if (this.autosaveInterval !== undefined) {
            clearInterval(this.autosaveInterval);
        }
    }

    get hasAstExpressions(): boolean {
        return !!(this.proofExercise?.sourceExpression && this.proofExercise?.targetExpression);
    }

    get allRules(): RewriteRuleModel[] {
        return this.blocks().flatMap((b) => b.rules ?? []);
    }

    get operatorBlocks(): BlockDefinitionModel[] {
        return this.blocks().filter((b) => (b.slots?.length ?? 0) >= 2);
    }

    get selectedRuleName(): string {
        return this.allRules.find((r) => r.id === this.selectedRuleId())?.name ?? this.selectedRuleId();
    }

    getRuleName(ruleId: string): string {
        return this.allRules.find((r) => r.id === ruleId)?.name ?? ruleId;
    }

    isSelectedPath(path: number[]): boolean {
        const sel = this.selectedNodePath();
        if (sel === undefined) return false;
        return sel.length === path.length && sel.every((v, i) => v === path[i]);
    }

    /** Select a rule from the palette. Auto-applies if a node is already highlighted. */
    selectRule(ruleId: string): void {
        this.selectedRuleId.set(ruleId);
        this.ruleApplicationError.set(undefined);
        if (this.selectedNodePath() !== undefined) {
            this.applySelectedRule();
        }
    }

    /** Select a node in the expression canvas. Auto-applies if a rule is already selected. */
    selectNode(path: number[]): void {
        this.selectedNodePath.set(path);
        this.ruleApplicationError.set(undefined);
        if (this.selectedRuleId()) {
            this.applySelectedRule();
        }
    }

    applySelectedRule() {
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
        this.stepStatuses.update((s) => [...s, 'pending']);
        this.stepErrors.update((s) => [...s, undefined]);
        this.currentExpression.set(newTree);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.hasUnsavedChanges.set(true);
    }

    // ── Rule palette DnD ──────────────────────────────────────────────────────

    onRuleDragStart(event: DragEvent, ruleId: string): void {
        this.draggingPayload.set(ruleId);
        event.dataTransfer!.setData('text/plain', ruleId);
        event.dataTransfer!.effectAllowed = 'copy';
    }

    onRuleDragEnd(): void {
        this.draggingPayload.set(undefined);
    }

    // ── Block palette DnD (gap merging) ───────────────────────────────────────

    onBlockDragStart(event: DragEvent, blockType: string): void {
        this.draggingPayload.set(blockType);
        event.dataTransfer!.setData('text/plain', blockType);
        event.dataTransfer!.effectAllowed = 'copy';
    }

    onBlockDragEnd(): void {
        this.draggingPayload.set(undefined);
    }

    handleGapDrop(event: { gapIndex: number; payload: string }): void {
        const { gapIndex, payload } = event;
        const block = this.blocks().find((b) => b.type === payload);
        if (!block) return;

        const roots = this.submissionRootNodes();
        const isMerge = (block.slots?.length ?? 0) >= 2 && gapIndex > 0 && gapIndex < roots.length;

        if (isMerge) {
            const left = roots[gapIndex - 1];
            const right = roots[gapIndex];
            const sortedSlots = [...(block.slots ?? [])].sort();
            const slots: Record<string, MathNode[]> = {};
            for (const slot of block.slots ?? []) slots[slot] = [{ type: 'number', value: '?' }];
            slots[sortedSlots[0]] = [left];
            slots[sortedSlots[1]] = [right];
            this.currentExpression.set({ type: payload, slots });
        } else if (roots.length === 0) {
            this.currentExpression.set(this.buildNode(payload));
        }

        this.draggingPayload.set(undefined);
        this.hasUnsavedChanges.set(true);
    }

    private buildNode(blockType: string): MathNode {
        const block = this.blocks().find((b) => b.type === blockType);
        if (!block || !block.slots?.length) {
            return { type: blockType, value: '?' };
        }
        const slots: Record<string, MathNode[]> = {};
        for (const slot of block.slots) {
            slots[slot] = [{ type: 'number', value: '?' }];
        }
        return { type: blockType, slots };
    }

    // ── Undo / Verify ─────────────────────────────────────────────────────────

    undoLastStep() {
        this.steps.update((s) => {
            const updated = s.slice(0, -1);
            const expr = updated.length > 0 ? updated[updated.length - 1].resultExpression : this.proofExercise?.sourceExpression;
            this.currentExpression.set(expr);
            return updated;
        });
        this.stepStatuses.update((s) => s.slice(0, -1));
        this.stepErrors.update((s) => s.slice(0, -1));
        this.ruleApplicationError.set(undefined);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.hasUnsavedChanges.set(true);
    }

    verifyFullProof() {
        const source = this.proofExercise?.sourceExpression;
        if (!source) return;

        const statuses: StepStatus[] = [];
        const errors: (string | undefined)[] = [];
        const stepsSnapshot = this.steps();

        stepsSnapshot.forEach((step, index) => {
            const rule = this.allRules.find((r) => r.id === step.appliedRuleId);
            if (!rule) {
                statuses.push('invalid');
                errors.push('Unknown rule: ' + step.appliedRuleId);
                return;
            }
            const prev = index === 0 ? source : stepsSnapshot[index - 1].resultExpression;
            const result = applyRule(prev, step.targetNodePath, rule.pattern, rule.template);
            const isValid = !!result && mathNodesEqual(result, step.resultExpression);
            statuses.push(isValid ? 'valid' : 'invalid');
            errors.push(isValid ? undefined : 'Rule does not produce this result from the previous expression.');
        });

        this.stepStatuses.set(statuses);
        this.stepErrors.set(errors);
    }

    get allStepsValid(): boolean {
        const statuses = this.stepStatuses();
        return statuses.length > 0 && statuses.every((s) => s === 'valid');
    }

    // ── Save / Submit ─────────────────────────────────────────────────────────

    save(silent = false) {
        if (!this.submission || !this.proofExercise) {
            return;
        }
        this.isSaving = true;
        this.submission.steps = this.steps();

        const observable = this.submission.id
            ? this.proofSubmissionService.update(this.submission, this.proofExercise.id!)
            : this.proofSubmissionService.create(this.submission, this.proofExercise.id!);

        observable.subscribe({
            next: (response) => {
                this.submission = response.body!;
                this.isSaving = false;
                this.hasUnsavedChanges.set(false);
                this.lastSavedAt.set(new Date());
                if (!silent) {
                    this.alertService.success('artemisApp.proofExercise.saveSuccessful');
                }
            },
            error: () => {
                this.isSaving = false;
                if (!silent) {
                    this.alertService.error('artemisApp.proofExercise.saveFailed');
                }
            },
        });
    }

    submit() {
        if (!this.submission || !this.proofExercise) {
            return;
        }
        this.submission.submitted = true;
        this.submission.steps = this.steps();

        const observable = this.submission.id
            ? this.proofSubmissionService.update(this.submission, this.proofExercise.id!)
            : this.proofSubmissionService.create(this.submission, this.proofExercise.id!);

        observable.subscribe({
            next: (response) => {
                this.submission = response.body!;
                this.hasUnsavedChanges.set(false);
                this.lastSavedAt.set(new Date());
                this.alertService.success('artemisApp.proofExercise.submitSuccessful');

                if (this.submission.results && this.submission.results.length > 0) {
                    this.result = this.submission.results[0];
                }
            },
            error: () => {
                this.submission.submitted = false;
                this.alertService.error('artemisApp.proofExercise.submitFailed');
            },
        });
    }
}
