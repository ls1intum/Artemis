import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    faArrowUpRightFromSquare,
    faCircleCheck,
    faCircleInfo,
    faCircleXmark,
    faLock,
    faPlus,
    faRotateRight,
    faSpinner,
    faTrashCan,
    faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import {
    TumAetUiButtonDirective,
    TumAetUiEmptyStateComponent,
    TumAetUiMessageComponent,
    TumAetUiPanelComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiTagComponent,
} from '@tumaet/ui-angular';
import { Subscription, catchError, forkJoin, of } from 'rxjs';

import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import {
    SecurityActivationStatus,
    SecurityAresApiUse,
    SecurityFrameworkConfig,
    SecurityFrameworkVersionOption,
    SecurityVersionCompatibilityVerdict,
} from 'app/programming/shared/entities/security-framework-config.model';
import {
    SecurityPolicyChange,
    SecurityPolicyChangeEntry,
    SecurityPolicyEditor,
    SecurityPolicyPreview,
    SecurityPolicyReadOnlyReason,
    SecurityTestStructure,
} from 'app/programming/shared/entities/security-policy.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
import { SecurityActivationCardComponent, SecurityCardState } from 'app/programming/manage/update/update-components/security/policies/security-activation-card.component';
import { SecurityActivationDialogComponent, SecurityActivationMode } from 'app/programming/manage/update/update-components/security/policies/security-activation-dialog.component';
import { SecurityConfirmDialogComponent, SecurityTranslatable } from 'app/programming/manage/update/update-components/security/policies/security-confirm-dialog.component';
import { SecurityPolicyPanelComponent } from 'app/programming/manage/update/update-components/security/policies/security-policy-panel.component';
import { AssignmentChange, SecurityReviewDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-review-dialog.component';
import {
    SecurityImportNotice,
    SecurityYamlDialogComponent,
    SecurityYamlImport,
} from 'app/programming/manage/update/update-components/security/policies/security-yaml-dialog.component';
import {
    EditorPolicy,
    PolicyChangeSummary,
    classesOfTests,
    committedName,
    countKey,
    editorPolicyOf,
    fileNameOf,
    isNewPolicy,
    listChanges,
    newPolicyDocument,
    parseAresDate,
    policyPathFor,
    rebasePolicy,
    summarizeChanges,
    withAssignment,
} from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

const PREFIX = 'artemisApp.programmingExercise.security.advanced';

/** How long the editor waits after the last change before it checks the policies against the pinned Ares version. */
const VERSION_CHECK_DELAY_MS = 600;

/** The language configuration that also scans the libraries of the student code, and the one that does not. */
const SCANNING = '_WALA_AND_INSTRUMENTATION';
const NOT_SCANNING = '_ARCHUNIT_AND_ASPECTJ';
const ANALYSIS_AND_WEAVING = /_(ARCHUNIT|WALA)_AND_(ASPECTJ|INSTRUMENTATION)$/;

/** The message below the section (screens "Follow the Saving Progress"). */
type Banner =
    | { kind: 'committing' }
    | { kind: 'committed'; titleKey: string; textKey: string; hash: string }
    | { kind: 'conflict'; authorName?: string; hash?: string }
    | { kind: 'failed'; message: string; commitMessage: string; retry?: 'review' }
    /** The change is pushed, but the builds it should start did not; Retry resumes them. */
    | { kind: 'processingFailed'; hash?: string }
    | { kind: 'orphaned'; files: string }
    | { kind: 'info'; text: string };

/** A commit request with the options it ran with, so that Retry repeats exactly it. */
interface CommitRequest {
    change: SecurityPolicyChange;
    keepUnsaved: boolean;
    scan: 'scanOn' | 'scanOff' | undefined;
}

/** The unsaved changes of all policies, by kind, as the commit bar lists them. */
interface ChangeParts {
    restrictions: number;
    behaviours: number;
    testCases: number;
    testClasses: number;
    settings: number;
    build: number;
    total: number;
}

const CHANGE_KINDS: readonly (keyof ChangeParts)[] = ['restrictions', 'behaviours', 'testCases', 'testClasses', 'settings', 'build'];

/**
 * The Security section of an existing exercise in Advanced mode (Objective 2): the activation card, the Manage Security Policies section and the messages
 * below it. It loads the policies from the tests repository, keeps every change as an unsaved change of a working copy, and commits all of them, a staged
 * framework version included, at once after a review of the diff. Turning the sandbox or dependency scanning on or off is committed at once after a
 * confirmation. A commit is refused if the policies changed in the repository meanwhile; a reload then keeps the unsaved changes on top of the new commit.
 */
@Component({
    selector: 'jhi-programming-exercise-security-policies',
    templateUrl: './programming-exercise-security-policies.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        NgTemplateOutlet,
        RouterLink,
        TumAetUiButtonDirective,
        TumAetUiTagComponent,
        TumAetUiMessageComponent,
        TumAetUiPanelComponent,
        TumAetUiEmptyStateComponent,
        TumAetUiProgressSpinnerComponent,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        SecurityActivationCardComponent,
        SecurityActivationDialogComponent,
        SecurityPolicyPanelComponent,
        SecurityReviewDialogComponent,
        SecurityYamlDialogComponent,
        SecurityConfirmDialogComponent,
    ],
})
export class ProgrammingExerciseSecurityPoliciesComponent {
    private readonly securityService = inject(SecurityFrameworkService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly countKey = countKey;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faLock = faLock;
    protected readonly faPlus = faPlus;
    protected readonly faRotateRight = faRotateRight;
    protected readonly faSpinner = faSpinner;
    protected readonly faTrashCan = faTrashCan;
    protected readonly faTriangleExclamation = faTriangleExclamation;

    readonly exerciseId = input.required<number>();
    /** The state of the Security Framework, as the activation card of Objective 1 loads and changes it. */
    readonly config = input.required<SecurityFrameworkConfig>();
    readonly versionOptions = input<SecurityFrameworkVersionOption[]>([]);
    readonly latestVersion = input<string | undefined>(undefined);
    /** The version the activation dialog preselects. */
    readonly activationVersion = input<string | undefined>(undefined);
    readonly aresApiUses = input<SecurityAresApiUse[]>([]);
    /** Whether the user may commit; editors who are not instructors see the policies read-only. */
    readonly canCommit = input(false);
    /** Whether the exercise is being saved, which locks every control. */
    readonly locked = input(false);
    /** The route to the tests repository; undefined where there is none. */
    readonly repositoryLink = input<(string | number)[] | undefined>(undefined);

    /** Asks to turn the sandbox on with the version. */
    readonly activateRequested = output<string>();
    /** Asks to turn the sandbox off. */
    readonly deactivateRequested = output<void>();
    /** Emits the Security Framework state after a commit of this view. */
    readonly configChange = output<SecurityFrameworkConfig>();
    /** Emits whether a commit of this view waits for its answer; the card must not change the repository meanwhile, also while simple mode hides this view. */
    readonly committingChange = output<boolean>();

    readonly sectionCollapsed = signal(true);
    readonly loading = signal(false);
    readonly loadFailed = signal(false);
    readonly editor = signal<SecurityPolicyEditor | undefined>(undefined);
    readonly policies = signal<EditorPolicy[]>([]);
    /** Committed policies deleted since the commit; the commit removes their files. */
    readonly deletedPolicies = signal<EditorPolicy[]>([]);
    /** The version the next commit pins; undefined to keep the pinned one. */
    readonly stagedVersion = signal<string | undefined>(undefined);
    readonly structure = signal<SecurityTestStructure | undefined>(undefined);
    readonly templateFiles = signal<string[] | undefined>(undefined);
    readonly templateFilesTruncated = signal(false);
    readonly banner = signal<Banner | undefined>(undefined);
    /** The verdict of the version the changes are committed with, checked a moment after each change. */
    readonly versionCheck = signal<SecurityPolicyPreview | undefined>(undefined);

    readonly dialogMode = signal<SecurityActivationMode>('activate');
    readonly dialogOpen = signal(false);
    /** The toggle position a confirmation shows until the instructor decides. */
    readonly pendingToggle = signal<boolean | undefined>(undefined);
    /** The requested state of dependency scanning while its dialog is open or its commit runs, so the switch shows it until then. */
    readonly pendingScan = signal<boolean | undefined>(undefined);
    readonly reviewOpen = signal(false);
    readonly reviewPreview = signal<SecurityPolicyPreview | undefined>(undefined);
    readonly discardOpen = signal(false);
    readonly deleteOpen = signal(false);
    readonly deletingPolicyId = signal<string | undefined>(undefined);
    readonly yamlOpen = signal(false);
    readonly yamlPolicyId = signal<string | undefined>(undefined);
    readonly yamlUnsaved = signal<string | undefined>(undefined);
    readonly importNotice = signal<SecurityImportNotice | undefined>(undefined);

    private newPolicyCounter = 0;
    /** The last commit request that failed, for Retry. */
    private failedRequest: CommitRequest | undefined;
    /** Set while the policies of a pushed but unprocessed commit are not read yet, so loading them again reads them, see loadPushed. */
    private pendingRecovery: { keepUnsaved: boolean } | undefined;
    /** The number of the latest YAML import started for each policy, from {@link importSequence}. */
    private readonly importGenerations = new Map<string, number>();
    /** The number of YAML imports started. */
    private importSequence = 0;
    /** Counts the replacements of the unsaved changes, so the YAML dialog drops a file it was still reading when they were replaced. */
    readonly yamlImportEpoch = signal(0);
    /** The number of lookups of who caused a conflict, so only the one of the conflict still shown applies. */
    private conflictLookups = 0;
    /** The number of loads of the policies started, so only the latest one applies. */
    private loadRequests = 0;
    /** The numbers of the YAML imports that have not answered yet and can still apply; the unsaved changes cannot be committed until they have. */
    private readonly pendingImportGenerations = signal<ReadonlySet<number>>(new Set());
    readonly pendingImports = computed(() => this.pendingImportGenerations().size);
    /** Whether the YAML dialog is reading a selected file, which becomes an import once it is read. */
    readonly yamlFileReading = signal(false);
    /** Whether an import is on its way, from reading the file to applying the answer; the unsaved changes cannot be committed meanwhile. */
    readonly importPending = computed(() => this.yamlFileReading() || this.pendingImports() > 0);
    /** The change the review shows, serialized, and the number of review previews requested. */
    private reviewedChange: string | undefined;
    private reviewRequests = 0;
    /** The number of previews of unsaved YAML started. */
    private yamlPreviewCount = 0;
    private structureRequested = false;
    /** The commit the test structure was requested for, and the number of requests. */
    private structureCommit: string | undefined;
    private structureRequests = 0;
    private templateFilesRequested = false;
    private versionCheckTimer?: ReturnType<typeof setTimeout>;
    private versionCheckSubscription?: Subscription;
    private lastStatus?: SecurityActivationStatus;

    readonly status = computed(() => this.config().status);
    /** Whether a commit request is pending; tracked apart from the banner that reports it, which a late answer of another request may replace. */
    private readonly commitPending = signal(false);
    readonly isCommitting = computed(() => this.commitPending());
    readonly cardState = computed<SecurityCardState>(() => {
        switch (this.status()) {
            case SecurityActivationStatus.GENERATING:
                return 'turningOn';
            case SecurityActivationStatus.DELETING:
                return 'turningOff';
            case SecurityActivationStatus.CONFIGURED:
                return this.isCommitting() ? 'committing' : 'configured';
            default:
                return 'inactive';
        }
    });
    readonly isConfigured = computed(() => this.status() === SecurityActivationStatus.CONFIGURED);
    readonly toggleOn = computed(() => this.pendingToggle() ?? (this.isConfigured() || this.status() === SecurityActivationStatus.GENERATING));
    /** Editing is paused while a commit is processed or the exercise is saved, and for users who may not commit. */
    readonly editingDisabled = computed(() => this.isCommitting() || this.locked() || !this.canCommit() || !this.isConfigured() || this.loadFailed());

    readonly presets = computed(() => this.editor()?.presets ?? []);
    readonly pinnedVersion = computed(() => this.editor()?.frameworkVersion ?? this.config().frameworkVersion);
    readonly shownVersion = computed(() => this.stagedVersion() ?? this.pinnedVersion());
    readonly shortBaseCommit = computed(() => this.editor()?.baseCommitHash?.substring(0, 7));

    /** Whether every committed policy scans dependencies: it uses WALA and the instrumentation of Ares. */
    /** The policies with those staged for deletion: the tests repository keeps their files until the deletion is committed, so scanning covers them. */
    private readonly policiesWithDeleted = computed(() => [...this.policies(), ...this.deletedPolicies()]);
    readonly scanDependencies = computed(() => {
        const documents = this.policiesWithDeleted()
            .map((policy) => policy.base)
            .filter((document) => document !== undefined);
        return documents.length > 0 && documents.every((document) => (document.languageConfiguration ?? '').endsWith(SCANNING));
    });
    /** The state the scanning switch shows: the requested one while it is confirmed or committed. */
    readonly shownScanDependencies = computed(() => this.pendingScan() ?? this.scanDependencies());
    /** Whether a committed policy is read-only: dependency scanning changes every policy, so it cannot be switched until the policy is fixed. */
    readonly scanBlockedByReadOnly = computed(() => this.policiesWithDeleted().some((policy) => !!policy.file && !policy.base));
    readonly scanAvailable = computed(
        () => !!this.editor()?.scanDependenciesAvailable && this.policiesWithDeleted().some((policy) => !!policy.base) && !this.scanBlockedByReadOnly(),
    );
    readonly strictTimeouts = computed(() => this.policies().some((policy) => !!policy.working?.testBehavior?.strictTimeout));

    /** The path each policy is saved to, by its id. */
    readonly paths = computed(() => {
        const existingPaths = this.policies()
            .map((policy) => policy.file?.policyPath)
            .filter((path) => path !== undefined);
        return new Map(this.policies().map((policy) => [policy.id, policyPathFor(policy.name, policy.file?.policyPath, existingPaths)]));
    });

    readonly summaries = computed(() => new Map(this.policies().map((policy) => [policy.id, summarizeChanges(policy)])));

    /** The summary of a policy without changes, for a panel whose summary is not computed yet. */
    protected readonly noChanges = summarizeChanges(editorPolicyOf({ policyPath: '' }));

    readonly changeParts = computed<ChangeParts>(() => {
        const parts: ChangeParts = { restrictions: 0, behaviours: 0, testCases: 0, testClasses: 0, settings: 0, build: 0, total: 0 };
        this.summaries().forEach((summary: PolicyChangeSummary) => {
            parts.restrictions += summary.restrictions;
            parts.behaviours += summary.behaviours;
            parts.testCases += summary.testCases;
            parts.testClasses += summary.testClasses;
            parts.settings += summary.settings + (summary.isNew ? 1 : 0);
            parts.total += summary.total;
        });
        parts.settings += this.deletedPolicies().length;
        parts.total += this.deletedPolicies().length;
        if (this.stagedVersion()) {
            parts.build = 1;
            parts.total++;
        }
        return parts;
    });

    readonly totalChanges = computed(() => this.changeParts().total);

    /** The unsaved changes as the commit bar lists them, e.g. "2 allowed resource accesses · 1 set test behaviour". */
    readonly changeText = computed(() => {
        const parts = this.changeParts();
        return CHANGE_KINDS.filter((kind) => parts[kind] > 0)
            .map((kind) => this.translateService.instant(countKey(`${PREFIX}.commitBar.${kind}`, parts[kind]), { count: parts[kind] }) as string)
            .join(' · ');
    });

    /** The tests of each policy with its label, so the other policies lock them. */
    readonly testsByPolicy = computed(() =>
        this.policies().map((policy) => ({ id: policy.id, tests: policy.tests, label: policy.name || fileNameOf(this.paths().get(policy.id) ?? '') })),
    );

    readonly yamlPolicy = computed(() => this.policies().find((policy) => policy.id === this.yamlPolicyId()));
    readonly deletingPolicy = computed(() => this.policies().find((policy) => policy.id === this.deletingPolicyId()));
    readonly readOnlyPolicies = computed(() => this.policies().filter((policy) => !policy.working));
    /** The files the policies are saved to, for the dialog that deletes them. */
    readonly policyPaths = computed(() => [...this.paths().values()].filter((path) => path.length > 0));

    /** What keeps the changes from being committed, translated; empty if they can be committed. */
    readonly problems = computed(() => {
        const problems: string[] = [];
        const paths = new Map<string, number>();
        for (const policy of this.policies()) {
            const path = this.paths().get(policy.id) ?? '';
            paths.set(path, (paths.get(path) ?? 0) + 1);
            if (!policy.working) {
                continue;
            }
            if (!policy.name.trim() || !path) {
                problems.push(this.translateService.instant(`${PREFIX}.problem.nameRequired`));
                continue;
            }
            const hidden = policy.working.testBehavior?.hiddenTests;
            const from = parseAresDate(hidden?.hiddenFrom);
            const until = parseAresDate(hidden?.hiddenUntil);
            if (from && until && !until.isAfter(from)) {
                problems.push(this.translateService.instant(`${PREFIX}.problem.hiddenPeriod`, { policy: policy.name }));
            }
            if (!policy.tests.length) {
                problems.push(this.translateService.instant(`${PREFIX}.problem.noTests`, { policy: policy.name }));
            }
        }
        if ([...paths.entries()].some(([path, count]) => path && count > 1)) {
            problems.push(this.translateService.instant(`${PREFIX}.problem.sameFile`));
        }
        return problems;
    });

    /** What each changed file of the review is. */
    readonly reviewDescriptions = computed(() => {
        const descriptions = new Map<string, string>();
        for (const policy of this.policies()) {
            const path = this.paths().get(policy.id) ?? '';
            let description = policy.name;
            if (isNewPolicy(policy)) {
                description += ` · ${this.translateService.instant(`${PREFIX}.review.newFile`)}`;
            } else if (policy.file?.policyPath !== path) {
                description += ` · ${this.translateService.instant(`${PREFIX}.review.renamedFrom`, { file: fileNameOf(policy.file?.policyPath ?? '') })}`;
            }
            descriptions.set(path, description);
        }
        this.deletedPolicies().forEach((policy) =>
            descriptions.set(policy.file?.policyPath ?? '', `${committedName(policy)} · ${this.translateService.instant(`${PREFIX}.review.deleted`)}`),
        );
        const buildSettings = this.translateService.instant(`${PREFIX}.review.buildSettings`);
        descriptions.set('build.gradle', buildSettings);
        descriptions.set('pom.xml', buildSettings);
        return descriptions;
    });

    /** The test case assignments the commit changes. */
    readonly reviewAssignments = computed<AssignmentChange[]>(() => {
        const changes: AssignmentChange[] = [];
        for (const policy of this.policies()) {
            const file = fileNameOf(this.paths().get(policy.id) ?? '');
            const { added, removed } = listChanges(policy.baseTests, policy.tests);
            added.forEach((entry) => changes.push({ sign: '+', entry, file }));
            removed.forEach((entry) => changes.push({ sign: '−', entry, file }));
        }
        this.deletedPolicies().forEach((policy) => policy.baseTests.forEach((entry) => changes.push({ sign: '−', entry, file: fileNameOf(policy.file?.policyPath ?? '') })));
        return changes;
    });

    readonly reviewSummary = computed(() => {
        const parts = this.changeParts();
        const texts = CHANGE_KINDS.filter((kind) => parts[kind] > 0).map(
            (kind) => this.translateService.instant(countKey(`${PREFIX}.review.changed.${kind}`, parts[kind]), { count: parts[kind] }) as string,
        );
        texts.push(this.translateService.instant(`${PREFIX}.review.basedOn`, { commit: this.shortBaseCommit() ?? '' }));
        return texts.join(' · ');
    });

    /** The proposed commit message, e.g. "Update Stress Tests (3 changes)". */
    readonly defaultCommitMessage = computed(() => {
        const changed = this.policies()
            .filter((policy) => (this.summaries().get(policy.id)?.total ?? 0) > 0)
            .map((policy) => policy.name);
        const subject = changed.length ? changed.join(', ') : this.translateService.instant(`${PREFIX}.review.securitySettings`);
        return this.translateService.instant(countKey(`${PREFIX}.review.defaultMessage`, this.totalChanges()), { subject, count: this.totalChanges() });
    });

    readonly versionTooOld = computed(() => this.versionCheck()?.verdict === SecurityVersionCompatibilityVerdict.INCOMPATIBLE);

    constructor() {
        // The editor follows the state of the sandbox: it loads the policies when the sandbox settles, and drops the unsaved changes when it is turned off.
        effect(() => {
            const exerciseId = this.exerciseId();
            const status = this.status();
            untracked(() => this.onStatus(exerciseId, status));
        });
        // A YAML dialog that is gone, e.g. because its policy was discarded, reads no file any more.
        effect(() => {
            if (!this.yamlPolicy()) {
                untracked(() => this.yamlFileReading.set(false));
            }
        });
        // The open review shows the diff of the change as it is, also after it changed meanwhile, e.g. because an import answered.
        effect(() => {
            if (!this.reviewOpen()) {
                return;
            }
            const change = JSON.stringify(this.buildChange());
            untracked(() => {
                if (change !== this.reviewedChange) {
                    this.previewReview();
                }
            });
        });
        // Check the unsaved changes against the version they are committed with a moment after the last change, so the banner warns before the review.
        effect(() => {
            const change = this.buildChange();
            const valid = this.problems().length === 0;
            untracked(() => this.scheduleVersionCheck(change, valid));
        });
        this.destroyRef.onDestroy(() => {
            clearTimeout(this.versionCheckTimer);
            this.versionCheckSubscription?.unsubscribe();
        });
    }

    /** The changes a commit makes. */
    private buildChange(commitMessage?: string): SecurityPolicyChange {
        const entries: SecurityPolicyChangeEntry[] = this.policies().flatMap((policy) =>
            policy.working && (this.summaries().get(policy.id)?.total ?? 0) > 0
                ? [
                      {
                          previousPolicyPath: policy.file?.policyPath,
                          document: cloneWith(policy.working, { policyPath: this.paths().get(policy.id) ?? '' }),
                          assignedTests: [...policy.tests],
                      },
                  ]
                : [],
        );
        return {
            baseCommitHash: this.editor()?.baseCommitHash,
            commitMessage,
            frameworkVersion: this.stagedVersion(),
            policies: entries,
            removedPolicyPaths: this.deletedPolicies().map((policy) => policy.file?.policyPath ?? ''),
        };
    }

    private onStatus(exerciseId: number, status: SecurityActivationStatus): void {
        const previous = this.lastStatus;
        this.lastStatus = status;
        if (status !== SecurityActivationStatus.CONFIGURED && status !== SecurityActivationStatus.INACTIVE) {
            return;
        }
        if (status === SecurityActivationStatus.INACTIVE) {
            this.deletedPolicies.set([]);
            this.stagedVersion.set(undefined);
        }
        if (previous !== status) {
            if (status === SecurityActivationStatus.CONFIGURED && this.editor() && this.totalChanges() > 0) {
                this.reload();
            } else {
                this.load(exerciseId);
            }
        }
    }

    /** Loads the policies and replaces every unsaved change. */
    private load(exerciseId: number): void {
        const request = ++this.loadRequests;
        if (this.pendingRecovery) {
            // This load supersedes the reads of a pushed but unprocessed commit, which therefore no longer end its pending state.
            this.pendingRecovery = undefined;
            this.setCommitPending(false);
        }
        this.loading.set(true);
        this.loadFailed.set(false);
        this.securityService.getPolicies(exerciseId).subscribe({
            next: (editor) => {
                if (request === this.loadRequests) {
                    this.applyEditor(editor);
                    this.loading.set(false);
                }
            },
            error: () => {
                if (request === this.loadRequests) {
                    this.loading.set(false);
                    this.loadFailed.set(true);
                }
            },
        });
    }

    protected retryLoad(): void {
        const recovery = this.pendingRecovery;
        if (recovery) {
            this.loadPushed(recovery.keepUnsaved);
        } else {
            this.load(this.exerciseId());
        }
    }

    /**
     * Loads the policies and the config of a change that was pushed although its processing failed. A commit of the unsaved changes made exactly them, since
     * editing is paused while it runs, so they are replaced; a commit of dependency scanning kept them, so they are rebased. The reads still belong to the
     * commit: until both answer, editing and switching the sandbox stay paused, so their answers overwrite neither an edit nor a later operation.
     */
    private loadPushed(keepUnsaved: boolean): void {
        const request = ++this.loadRequests;
        this.pendingRecovery = { keepUnsaved };
        // A repeated read, after the first one failed, is part of the commit as well.
        this.setCommitPending(true);
        this.loading.set(true);
        forkJoin({
            editor: this.securityService.getPolicies(this.exerciseId()).pipe(catchError(() => of(undefined))),
            config: this.securityService.getConfig(this.exerciseId()).pipe(catchError(() => of(undefined))),
        }).subscribe(({ editor, config }) => {
            // A newer read or a newer commit owns the pending state, so an obsolete answer changes nothing, not even that state.
            if (request !== this.loadRequests) {
                return;
            }
            this.setCommitPending(false);
            this.loading.set(false);
            if (config) {
                this.configChange.emit(config);
            }
            if (!editor) {
                // The shown policies are older than the pushed ones, so Discard and Commit must not act on them: the editor shows the load error instead,
                // whose Retry reads the pushed policies, while the banner's Retry still resumes the builds.
                this.loadFailed.set(true);
                return;
            }
            this.pendingRecovery = undefined;
            this.loadFailed.set(false);
            if (keepUnsaved) {
                this.rebaseOnto(editor, false);
            } else {
                this.applyEditor(editor);
            }
            if (this.banner()?.kind === 'processingFailed') {
                this.banner.set({ kind: 'processingFailed', hash: editor.lastCommit?.hash.substring(0, 7) });
            }
        });
    }

    private applyEditor(editor: SecurityPolicyEditor): void {
        this.editor.set(editor);
        this.refreshStructure();
        this.dropPendingYamlRequests();
        this.policies.set((editor.policies ?? []).map(editorPolicyOf));
        this.deletedPolicies.set([]);
        this.stagedVersion.set(undefined);
    }

    /** Loads the policies again and keeps the unsaved changes: each changed policy is rebased onto its new committed version; new policies stay. */
    protected reload(announce = false): void {
        // Only the latest load applies, so an earlier one answering after it, or after a commit that followed it, never brings back an older state.
        const request = ++this.loadRequests;
        this.conflictLookups++;
        this.loading.set(true);
        this.securityService.getPolicies(this.exerciseId()).subscribe({
            // The requested scanning state stays: its dialog may still be open, and its commit sets the state when it is done.
            next: (editor) => {
                if (request === this.loadRequests) {
                    this.rebaseOnto(editor, announce);
                    this.loading.set(false);
                }
            },
            error: () => {
                if (request === this.loadRequests) {
                    this.loading.set(false);
                }
            },
        });
    }

    /** Shows the policies of the editor and rebases the unsaved changes made until now onto them, including those made while it was loading. */
    private rebaseOnto(editor: SecurityPolicyEditor, announce: boolean): void {
        const previous = this.policies();
        const deleted = this.deletedPolicies();
        this.editor.set(editor);
        this.refreshStructure();
        const files = editor.policies ?? [];
        const changed = (policy: EditorPolicy) => !!policy.working && (this.summaries().get(policy.id)?.total ?? 0) > 0;
        // A changed policy whose file can no longer be edited here, e.g. because someone broke its YAML, keeps its changes as a new policy.
        const unreadable: EditorPolicy[] = [];
        const reloaded = files
            .filter((file) => !deleted.some((policy) => policy.file?.policyPath === file.policyPath))
            .map((file) => {
                const old = previous.find((candidate) => candidate.file?.policyPath === file.policyPath);
                if (old && changed(old) && !file.document) {
                    unreadable.push(old);
                }
                return old && changed(old) ? rebasePolicy(old, file) : editorPolicyOf(file);
            });
        const languageConfiguration = files.find((file) => file.document)?.document?.languageConfiguration;
        // A new policy follows the committed policies, e.g. when dependency scanning was switched, unless the instructor gave it a configuration of its own.
        const added = previous
            .filter((policy) => isNewPolicy(policy))
            .map((policy) =>
                policy.working && languageConfiguration && !policy.ownLanguageConfiguration
                    ? cloneWith(policy, { working: cloneWith(policy.working, { languageConfiguration }) })
                    : policy,
            );
        // A changed policy whose file someone deleted or renamed meanwhile is kept as a new policy, so the changes are not lost without a word.
        const vanished = [
            ...previous.filter(
                (policy) =>
                    !!policy.file &&
                    changed(policy) &&
                    !files.some((file) => file.policyPath === policy.file?.policyPath) &&
                    !deleted.some((candidate) => candidate.id === policy.id),
            ),
            ...unreadable,
        ];
        const takenNames = [...reloaded, ...added].map((policy) => policy.name);
        const orphaned = vanished.map((policy) => {
            const kept = this.asNewPolicy(policy, takenNames);
            takenNames.push(kept.name);
            return kept;
        });
        this.policies.set([...reloaded, ...added, ...orphaned]);
        this.refreshYamlUnsaved();
        // A deletion stays staged for the file it names, which is read again, so its committed state, e.g. after a scanning commit, is current.
        this.deletedPolicies.set(files.filter((file) => deleted.some((policy) => policy.file?.policyPath === file.policyPath)).map(editorPolicyOf));
        if (this.stagedVersion() === editor.frameworkVersion) {
            this.stagedVersion.set(undefined);
        }
        if (vanished.length) {
            this.banner.set({ kind: 'orphaned', files: vanished.map((policy) => fileNameOf(policy.file?.policyPath ?? '')).join(', ') });
        } else if (announce) {
            this.banner.set({ kind: 'info', text: this.translateService.instant(`${PREFIX}.banner.reloaded`, { hash: editor.lastCommit?.hash.substring(0, 7) ?? '' }) });
        }
    }

    protected onToggleRequested(on: boolean): void {
        // A commit that is still running would answer after the sandbox was switched and bring back its own state.
        if (this.isCommitting()) {
            return;
        }
        this.pendingToggle.set(on);
        this.dialogMode.set(on ? 'activate' : 'deactivate');
        this.dialogOpen.set(true);
    }

    protected onScanRequested(on: boolean): void {
        this.pendingScan.set(on);
        this.dialogMode.set(on ? 'scanOn' : 'scanOff');
        this.dialogOpen.set(true);
    }

    protected onDialogConfirmed(version: string | undefined): void {
        this.pendingToggle.set(undefined);
        if (this.isCommitting()) {
            // The confirmation waited for a commit that started meanwhile; the switch returns to its state, and the instructor confirms again once it is done.
            this.pendingScan.set(undefined);
            return;
        }
        switch (this.dialogMode()) {
            case 'activate':
                if (version) {
                    this.activateRequested.emit(version);
                }
                break;
            case 'deactivate':
                this.discard(false);
                this.deactivateRequested.emit();
                break;
            case 'scanOn':
                this.commitScanning(true);
                break;
            case 'scanOff':
                this.commitScanning(false);
                break;
            default:
                break;
        }
    }

    protected onDialogCancelled(): void {
        this.pendingToggle.set(undefined);
        this.pendingScan.set(undefined);
    }

    protected onVersionChange(version: string): void {
        this.stagedVersion.set(version === this.pinnedVersion() ? undefined : version);
    }

    /** Commits only the language configuration of the committed policies, so the unsaved changes stay unsaved. */
    private commitScanning(on: boolean): void {
        const policies: SecurityPolicyChangeEntry[] = this.policiesWithDeleted().flatMap((policy) =>
            policy.base && policy.file
                ? [
                      {
                          previousPolicyPath: policy.file.policyPath,
                          document: cloneWith(policy.base, {
                              languageConfiguration: (policy.base.languageConfiguration ?? '').replace(ANALYSIS_AND_WEAVING, on ? SCANNING : NOT_SCANNING),
                          }),
                          assignedTests: [...policy.baseTests],
                      },
                  ]
                : [],
        );
        const commitMessage = this.translateService.instant(`${PREFIX}.${on ? 'scanOn' : 'scanOff'}.commitMessage`);
        this.runCommit({ baseCommitHash: this.editor()?.baseCommitHash, commitMessage, policies, removedPolicyPaths: [] }, true, on ? 'scanOn' : 'scanOff');
    }

    protected onPolicyChange(updated: EditorPolicy): void {
        this.policies.update((policies) => policies.map((existing) => (existing.id === updated.id ? updated : existing)));
        this.clearSettledBanner();
    }

    /**
     * @param takenNames the names of the other policies; the new one gets a number if its name is taken, so it is saved to a file of its own
     * @returns the policy as a new one with all of its current settings, assignments and rows as unsaved changes
     */
    private asNewPolicy(policy: EditorPolicy, takenNames: readonly string[]): EditorPolicy {
        this.newPolicyCounter++;
        let name = policy.name;
        for (let number = 2; takenNames.includes(name); number++) {
            name = `${policy.name} ${number}`;
        }
        return {
            id: `new-${this.newPolicyCounter}`,
            name,
            working: policy.working ? deepClone(policy.working) : undefined,
            baseTests: [],
            tests: [...policy.tests],
            baseExtraClasses: [],
            extraClasses: [...policy.extraClasses],
        };
    }

    protected addPolicy(): void {
        // Without a committed policy to copy the supervised code from, the one the server derives from the repository is used.
        const template = this.policies().find((policy) => policy.base)?.base ?? this.editor()?.newPolicyTemplate;
        let number = this.policies().length + 1;
        while (this.policies().some((policy) => policy.name === this.policyName(number))) {
            number++;
        }
        this.newPolicyCounter++;
        const policy: EditorPolicy = {
            id: `new-${this.newPolicyCounter}`,
            name: this.policyName(number),
            working: newPolicyDocument(template, ''),
            baseTests: [],
            tests: [],
            baseExtraClasses: [],
            extraClasses: [],
        };
        this.policies.update((policies) => [...policies, policy]);
        this.sectionCollapsed.set(false);
    }

    private policyName(number: number): string {
        return this.translateService.instant(`${PREFIX}.policy.defaultName`, { number });
    }

    protected askDeletePolicy(policy: EditorPolicy): void {
        this.deletingPolicyId.set(policy.id);
        this.deleteOpen.set(true);
    }

    protected deletePolicy(): void {
        const policy = this.deletingPolicy();
        if (!policy) {
            return;
        }
        this.policies.update((policies) => policies.filter((candidate) => candidate.id !== policy.id));
        if (!isNewPolicy(policy)) {
            this.deletedPolicies.update((deleted) => [...deleted, policy]);
        }
        this.deletingPolicyId.set(undefined);
    }

    readonly deleteHeader: SecurityTranslatable = { key: `${PREFIX}.deletePolicy.title` };
    readonly deleteQuestion = computed<SecurityTranslatable>(() => ({ key: `${PREFIX}.deletePolicy.question`, params: { policy: this.deletingPolicy()?.name ?? '' } }));
    readonly deleteWarning = computed<SecurityTranslatable>(() => {
        const policy = this.deletingPolicy();
        if (!policy || isNewPolicy(policy)) {
            return { key: `${PREFIX}.deletePolicy.neverCommitted` };
        }
        return { key: `${PREFIX}.deletePolicy.committed`, params: { file: fileNameOf(policy.file?.policyPath ?? ''), count: policy.baseTests.length } };
    });

    /** For each policy, by its id, the label of the policy capturing each test of the other policies. */
    readonly otherTestsByPolicy = computed(
        () =>
            new Map(
                this.testsByPolicy().map(({ id }) => {
                    const tests = new Map<string, string>();
                    this.testsByPolicy()
                        .filter((policy) => policy.id !== id)
                        .forEach((policy) => policy.tests.forEach((entry) => tests.set(entry, policy.label)));
                    return [id, tests];
                }),
            ),
    );

    /** The map for a panel whose policy is not computed yet. */
    protected readonly noOtherTests = new Map<string, string>();

    /**
     * Ignores the answers of YAML imports and renderings that are still pending, because the unsaved changes they belong to were replaced, e.g. discarded or
     * committed; an import answering later would bring them back.
     */
    /**
     * The rendering of the unsaved YAML belongs to the working copies it was made from; once they are rebased, it is dropped and, while the dialog is open,
     * rendered again.
     */
    private refreshYamlUnsaved(): void {
        this.yamlPreviewCount++;
        this.yamlUnsaved.set(undefined);
        if (this.yamlOpen()) {
            this.renderYamlUnsaved();
        }
    }

    private dropPendingYamlRequests(): void {
        this.importGenerations.clear();
        this.pendingImportGenerations.set(new Set());
        this.yamlPreviewCount++;
        this.yamlImportEpoch.update((epoch) => epoch + 1);
    }

    /** Loads the test structure of the commit the policies are based on, once per commit; only the answer to the latest request applies. */
    protected requestStructure(): void {
        const commit = this.editor()?.baseCommitHash;
        if (this.structureRequested && this.structureCommit === commit) {
            return;
        }
        this.structureRequested = true;
        this.structureCommit = commit;
        const request = ++this.structureRequests;
        this.securityService.getTestStructure(this.exerciseId()).subscribe({
            next: (structure) => {
                if (request === this.structureRequests) {
                    this.structure.set(structure);
                }
            },
            error: () => {
                if (request === this.structureRequests) {
                    this.structureRequested = false;
                    this.structure.set({ testClasses: [] });
                }
            },
        });
    }

    /**
     * Loads the test structure again once the policies are based on another commit, whose tests may differ, if it was loaded before. The old structure is
     * dropped at once, so the test case dialog waits for the new one instead of setting up its selection from test cases that may be gone.
     */
    private refreshStructure(): void {
        if (this.structureRequested && this.structureCommit !== this.editor()?.baseCommitHash) {
            this.structure.set(undefined);
            this.requestStructure();
        }
    }

    protected requestTemplateFiles(): void {
        if (this.templateFilesRequested) {
            return;
        }
        this.templateFilesRequested = true;
        this.securityService.getTemplateFiles(this.exerciseId()).subscribe({
            next: (files) => {
                this.templateFiles.set(files.paths ?? []);
                this.templateFilesTruncated.set(!!files.truncated);
            },
            error: () => {
                this.templateFilesRequested = false;
                this.templateFiles.set([]);
            },
        });
    }

    private scheduleVersionCheck(change: SecurityPolicyChange, valid: boolean): void {
        clearTimeout(this.versionCheckTimer);
        this.versionCheckSubscription?.unsubscribe();
        if ((!change.policies.length && !change.frameworkVersion) || !valid) {
            this.versionCheck.set(undefined);
            return;
        }
        this.versionCheckTimer = setTimeout(() => {
            this.versionCheckSubscription = this.securityService.previewPolicies(this.exerciseId(), change).subscribe({
                next: (preview) => this.versionCheck.set(preview),
                // A rejected preview, e.g. a value Ares never accepts, shows in the review; the banner only reports the version.
                error: () => this.versionCheck.set(undefined),
            });
        }, VERSION_CHECK_DELAY_MS);
    }

    protected openReview(): void {
        if (!this.totalChanges()) {
            return;
        }
        this.reviewOpen.set(true);
        this.previewReview();
    }

    /**
     * Shows the diff of the change as it is now. The review and its commit belong to this change: a newer preview replaces it, and an older one answering late
     * is ignored, so the diff shown is always the one committed.
     */
    private previewReview(): void {
        const change = this.buildChange();
        this.reviewedChange = JSON.stringify(change);
        const request = ++this.reviewRequests;
        const isCurrent = () => request === this.reviewRequests && this.reviewOpen();
        this.reviewPreview.set(undefined);
        if (this.problems().length) {
            // The problems show in the review; the server would refuse the preview of an incomplete change anyway.
            this.reviewPreview.set({ verdict: SecurityVersionCompatibilityVerdict.UNVERIFIED, files: [], frameworkVersion: this.shownVersion() });
            return;
        }
        this.securityService.previewPolicies(this.exerciseId(), change).subscribe({
            next: (preview) => {
                if (isCurrent()) {
                    this.reviewPreview.set(preview);
                }
            },
            error: (error: HttpErrorResponse) => {
                if (!isCurrent()) {
                    return;
                }
                this.reviewOpen.set(false);
                // Retry opens the review again, so nothing is committed without the diff; a failed commit from before is no longer retried.
                this.failedRequest = undefined;
                this.banner.set({ kind: 'failed', message: this.errorMessage(error), commitMessage: this.defaultCommitMessage(), retry: 'review' });
            },
        });
    }

    protected commit(commitMessage: string): void {
        if (this.importPending()) {
            return;
        }
        // The review commits what its diff shows; a change made since, e.g. by an import that answered late, is shown first.
        if (this.reviewOpen() && JSON.stringify(this.buildChange()) !== this.reviewedChange) {
            this.previewReview();
            return;
        }
        this.runCommit(this.buildChange(commitMessage), false, undefined);
    }

    /** Records whether a commit waits for its answer and tells the card when that changes. */
    private setCommitPending(pending: boolean): void {
        if (this.commitPending() === pending) {
            return;
        }
        this.commitPending.set(pending);
        this.committingChange.emit(pending);
    }

    /** Commits a change. A commit of the unsaved changes replaces them with the new commit; a commit of dependency scanning keeps them on top of it. */
    private runCommit(change: SecurityPolicyChange, keepUnsaved: boolean, scan: 'scanOn' | 'scanOff' | undefined): void {
        // While the policies load, the change may be based on a state the reload is about to replace.
        if (this.isCommitting() || this.loading()) {
            return;
        }
        this.banner.set({ kind: 'committing' });
        this.setCommitPending(true);
        this.conflictLookups++;
        this.failedRequest = undefined;
        this.securityService.commitPolicies(this.exerciseId(), change).subscribe({
            next: (result) => {
                this.setCommitPending(false);
                // A load that is still pending started before this commit, so its answer is older than the editor of the commit.
                this.loadRequests++;
                this.loading.set(false);
                // The editor of the commit is current, also after the pushed policies of an unprocessed commit could not be read.
                this.pendingRecovery = undefined;
                this.loadFailed.set(false);
                this.reviewOpen.set(false);
                const hash = (result.editor.lastCommit?.hash ?? result.editor.baseCommitHash ?? '').substring(0, 7);
                this.configChange.emit(result.config);
                this.versionCheck.set(undefined);
                if (keepUnsaved) {
                    // The unsaved changes are rebased onto the new commit, so the scanning switch shows the committed state at once.
                    this.rebaseOnto(result.editor, false);
                } else {
                    this.applyEditor(result.editor);
                }
                this.pendingScan.set(undefined);
                this.banner.set({
                    kind: 'committed',
                    titleKey: scan ? `${PREFIX}.${scan}.committedTitle` : `${PREFIX}.banner.committed`,
                    textKey: scan ? `${PREFIX}.${scan}.committedText` : `${PREFIX}.banner.committedText`,
                    hash,
                });
            },
            error: (error: HttpErrorResponse) => {
                // A pushed change whose processing failed stays pending until its policies and config are read again, see loadPushed.
                const processingFailed = isProcessingFailure(error);
                if (!processingFailed) {
                    this.setCommitPending(false);
                }
                this.reviewOpen.set(false);
                this.pendingScan.set(undefined);
                if (error.status === 409) {
                    this.banner.set({ kind: 'conflict' });
                    // Name who changed the policies; the unsaved changes stay until the instructor reloads. A later reload or commit supersedes this lookup.
                    const lookup = ++this.conflictLookups;
                    this.securityService.getPolicies(this.exerciseId()).subscribe({
                        next: (latest) => {
                            if (lookup === this.conflictLookups && this.banner()?.kind === 'conflict') {
                                this.banner.set({ kind: 'conflict', authorName: latest.lastCommit?.authorName, hash: latest.lastCommit?.hash.substring(0, 7) });
                            }
                        },
                        error: () => undefined,
                    });
                } else if (processingFailed) {
                    // The push succeeded, so the committed policies are the pushed ones, and Discard must return to them. Retry sends the same request again,
                    // whose processing the server resumes instead of applying the change twice; it keeps what was edited meanwhile.
                    this.failedRequest = { change, keepUnsaved: true, scan };
                    this.banner.set({ kind: 'processingFailed' });
                    this.loadPushed(keepUnsaved);
                } else {
                    this.failedRequest = { change, keepUnsaved, scan };
                    this.banner.set({ kind: 'failed', message: this.errorMessage(error), commitMessage: change.commitMessage ?? this.defaultCommitMessage() });
                }
            },
        });
    }

    /**
     * Repeats a failed commit. Dependency scanning repeats its own request, which leaves the unsaved changes as they are; a commit of the unsaved changes
     * is built again, so it includes what was edited since, and is the same request if nothing was.
     */
    protected retryCommit(): void {
        const banner = this.banner();
        const request = this.failedRequest;
        if (banner?.kind === 'processingFailed' && request) {
            this.runCommit(request.change, request.keepUnsaved, request.scan);
        } else if (banner?.kind === 'failed' && banner.retry === 'review') {
            this.openReview();
        } else if (request?.scan) {
            this.runCommit(request.change, request.keepUnsaved, request.scan);
        } else if (banner?.kind === 'failed') {
            this.commit(banner.commitMessage);
        }
    }

    /** Drops every unsaved change and returns to the committed state. */
    protected discard(announce = true): void {
        const editor = this.editor();
        if (editor) {
            this.policies.set((editor.policies ?? []).map(editorPolicyOf));
        }
        this.dropPendingYamlRequests();
        this.deletedPolicies.set([]);
        this.stagedVersion.set(undefined);
        // The builds of a pushed change that did not start still wait for Retry, which discarding does not affect.
        if (this.banner()?.kind !== 'processingFailed') {
            this.banner.set(announce ? { kind: 'info', text: this.translateService.instant(`${PREFIX}.banner.discarded`) } : undefined);
        }
    }

    readonly discardHeader: SecurityTranslatable = { key: `${PREFIX}.discard.title` };
    readonly discardQuestion = computed<SecurityTranslatable>(() => ({ key: countKey(`${PREFIX}.discard.question`, this.totalChanges()), params: { count: this.totalChanges() } }));
    readonly discardWarning = computed<SecurityTranslatable>(() => ({ key: `${PREFIX}.discard.warning`, params: { commit: this.shortBaseCommit() ?? '' } }));

    protected openYaml(policy: EditorPolicy): void {
        this.yamlPolicyId.set(policy.id);
        this.yamlUnsaved.set(undefined);
        this.importNotice.set(undefined);
        this.yamlOpen.set(true);
    }

    /** Renders the YAML of the policy shown in the YAML dialog with its unsaved changes. */
    protected renderYamlUnsaved(): void {
        // Only the latest preview of the policy the dialog still shows applies, so an earlier one that answers later does not replace it.
        const request = ++this.yamlPreviewCount;
        const policy = this.yamlPolicy();
        if (!policy?.working) {
            this.yamlUnsaved.set(policy?.file?.content);
            return;
        }
        const path = this.paths().get(policy.id);
        if (!path) {
            this.importNotice.set({ severity: 'error', text: this.translateService.instant(`${PREFIX}.problem.nameRequired`) });
            return;
        }
        const entry: SecurityPolicyChangeEntry = {
            previousPolicyPath: policy.file?.policyPath,
            document: cloneWith(policy.working, { policyPath: path }),
            assignedTests: policy.tests,
        };
        const isCurrent = () => request === this.yamlPreviewCount && this.yamlPolicyId() === policy.id;
        this.securityService.previewPolicies(this.exerciseId(), { baseCommitHash: this.editor()?.baseCommitHash, policies: [entry], removedPolicyPaths: [] }).subscribe({
            next: (preview) => {
                if (isCurrent()) {
                    this.yamlUnsaved.set(preview.files?.[0]?.after);
                }
            },
            error: (error: HttpErrorResponse) => {
                if (isCurrent()) {
                    this.importNotice.set({ severity: 'error', text: this.errorMessage(error) });
                }
            },
        });
    }

    /**
     * Replaces the policy shown in the YAML dialog with an imported file, as unsaved changes; the test cases stay assigned. A file that was selected for
     * another policy, e.g. because its read took long, is dropped.
     */
    protected importYaml(selected: SecurityYamlImport): void {
        const policy = this.yamlPolicy();
        const path = policy ? this.paths().get(policy.id) : undefined;
        const working = policy?.working;
        if (!policy || !working || !path || path !== selected.policyPath) {
            return;
        }
        // Only the latest import of a policy applies, so an earlier one that answers later does not replace it.
        // The numbers keep increasing across discards, so an import from before one never matches the latest import of the policy.
        const generation = ++this.importSequence;
        // A commit waits for the import, so the change it submits includes the imported file and the commit does not drop it afterwards. Only imports
        // that can still apply count: a newer import of the policy supersedes this one, and a discard or commit drops it.
        const superseded = this.importGenerations.get(policy.id);
        this.importGenerations.set(policy.id, generation);
        this.pendingImportGenerations.update((pending) => new Set([...pending].filter((candidate) => candidate !== superseded)).add(generation));
        const isLatest = () => this.importGenerations.get(policy.id) === generation;
        const settle = () => this.pendingImportGenerations.update((pending) => new Set([...pending].filter((candidate) => candidate !== generation)));
        this.securityService.importPolicy(this.exerciseId(), path, selected.content).subscribe({
            next: (imported) => {
                settle();
                // The policy may have changed while the file was read, so the import goes into its current state.
                const current = this.policies().find((candidate) => candidate.id === policy.id);
                if (!isLatest() || !current?.working) {
                    return;
                }
                if (imported.document) {
                    const document = cloneWith(imported.document, { policyPath: current.working.policyPath });
                    const classes = classesOfTests(current.tests);
                    const extraClasses = (document.testClasses ?? []).filter((testClass) => !testClass.includes('#') && !classes.includes(testClass));
                    // A new policy that imports another language configuration keeps it from now on, see rebaseOnto.
                    const ownLanguageConfiguration =
                        !!current.ownLanguageConfiguration || (isNewPolicy(current) && document.languageConfiguration !== current.working.languageConfiguration);
                    this.onPolicyChange(withAssignment(cloneWith(current, { working: document, ownLanguageConfiguration }), current.tests, extraClasses));
                    this.yamlUnsaved.set(undefined);
                    this.renderYamlUnsaved();
                    const changes = this.summaries().get(policy.id)?.total ?? 0;
                    this.importNotice.set({
                        severity: 'success',
                        text: this.translateService.instant(countKey(`${PREFIX}.yaml.imported`, changes), { count: changes, policy: current.name }),
                    });
                } else {
                    this.importNotice.set({
                        severity: 'error',
                        text: this.translateService.instant(`${PREFIX}.policy.readOnlyReason.${imported.readOnlyReason ?? SecurityPolicyReadOnlyReason.UNSUPPORTED_CONTENT}`, {
                            path,
                            line: imported.errorLine ?? '',
                        }),
                    });
                }
            },
            error: (error: HttpErrorResponse) => {
                settle();
                if (isLatest()) {
                    this.importNotice.set({ severity: 'error', text: this.errorMessage(error) });
                }
            },
        });
    }

    protected focusVersion(): void {
        // The id is the one of the trigger of the select, so the trigger itself takes the focus.
        const select = document.getElementById('securityAdvancedVersion');
        select?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        select?.focus({ preventScroll: true });
    }

    protected dismissBanner(): void {
        this.banner.set(undefined);
    }

    private clearSettledBanner(): void {
        const kind = this.banner()?.kind;
        if (kind === 'committed' || kind === 'failed' || kind === 'info') {
            this.banner.set(undefined);
        }
    }

    /** The message of a refused request: the server's translated explanation, or a generic one. */
    private errorMessage(error: HttpErrorResponse): string {
        const body = error.error as { title?: string; detail?: string; message?: string; params?: Record<string, unknown> } | undefined;
        if (typeof body?.message === 'string' && body.message.startsWith('error.')) {
            const params = typeof body.params === 'object' && body.params !== null ? body.params : undefined;
            const translated: string = this.translateService.instant(body.message, params);
            if (!translated.startsWith(translationNotFoundMessage)) {
                return translated;
            }
        }
        return body?.title ?? body?.detail ?? this.translateService.instant(`${PREFIX}.banner.failedGeneric`);
    }
}

/** Whether the server pushed the change but could not process it, e.g. start the builds; repeating the request resumes the processing. */
function isProcessingFailure(error: HttpErrorResponse): boolean {
    return (error.error as { message?: string } | null | undefined)?.message === 'error.securityFrameworkProcessingFailed';
}
