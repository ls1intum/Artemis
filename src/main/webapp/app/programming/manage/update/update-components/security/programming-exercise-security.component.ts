import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
    TumAetUiButtonDirective,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiMessageComponent,
    TumAetUiSelectButtonComponent,
    TumAetUiSelectComponent,
    TumAetUiToggleSwitchComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faQuestionCircle, faRotateRight, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import {
    SecurityActivationStatus,
    SecurityCardMode,
    SecurityFrameworkAction,
    SecurityFrameworkChoice,
    SecurityFrameworkConfig,
    SecurityFrameworkVersionOption,
    SecurityFrameworkVersions,
    SecurityPolicyFileProblem,
    TRANSIENT_SECURITY_STATUSES,
    UNREPAIRABLE_POLICY_FILE_PROBLEMS,
    isSameSecurityChoice,
    isSecurityActive,
    securityFrameworkProblemMessages,
} from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';

/** The three-way choice of an import whose Security Framework state is not known: keep it, or activate or deactivate the sandbox. */
export type SecurityImportAction = 'KEEP' | SecurityFrameworkAction;

const DEACTIVATE_CONFIRMATION_KEY = 'securityDeactivateConfirmation';

/**
 * "Activate Security Framework" card in the create, edit and import form of a programming exercise, shown in both
 * simple and advanced mode.
 *
 * Design principles:
 * - The card is rendered only for {@link SUPPORTED_LANGUAGES}; today just Java, whose templates ship with
 *   Ares enabled. Hence a new exercise starts out ACTIVE, and turning it off is the explicit choice.
 * - A single {@link SecurityActivationStatus} drives every visual (tint, pill, toggle, sync line,
 *   warning); the card can never show two contradictory states at once.
 * - The parent decides the {@link mode}. Only in `edit` mode does the card change a repository (through
 *   {@link SecurityFrameworkService}, the server commits every change to the tests repository). In `create` and
 *   `import` mode it only reads, and emits the {@link SecurityFrameworkChoice} the parent sends with its request.
 */
@Component({
    selector: 'jhi-programming-exercise-security',
    templateUrl: './programming-exercise-security.component.html',
    styleUrl: './programming-exercise-security.component.scss',
    providers: [TumAetUiConfirmationService],
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiToggleSwitchComponent,
        TumAetUiSelectComponent,
        TumAetUiSelectButtonComponent,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        TumAetUiButtonDirective,
        TumAetUiConfirmDialogComponent,
        FaIconComponent,
    ],
})
export class ProgrammingExerciseSecurityComponent {
    /** Programming languages the Security Framework supports. Extend this to roll out to more. */
    private static readonly SUPPORTED_LANGUAGES: ReadonlySet<ProgrammingLanguage> = new Set([ProgrammingLanguage.JAVA]);

    private readonly securityService = inject(SecurityFrameworkService);
    private readonly translateService = inject(TranslateService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);

    protected readonly SecurityActivationStatus = SecurityActivationStatus;
    protected readonly SecurityPolicyFileProblem = SecurityPolicyFileProblem;
    protected readonly DEACTIVATE_CONFIRMATION_KEY = DEACTIVATE_CONFIRMATION_KEY;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faRotateRight = faRotateRight;

    readonly programmingExercise = input.required<ProgrammingExercise>();
    /**
     * Which form the card is part of. Only `edit` sends write requests; during an import the exercise's id is the id of the
     * source exercise, so the card must never derive the mode from it.
     */
    readonly mode = input<SecurityCardMode>('create');
    /** The exercise an import copies from, whose config the card shows read-only. Absent for an import from a file or the sharing platform. */
    readonly sourceExerciseId = input<number | undefined>(undefined);
    /**
     * Selected language, passed as an explicit input from the wizard so the Java gate reacts to
     * language changes through Angular's binding (the parent mutates the exercise's language field in
     * place, which a read through the exercise input alone would not pick up in a zoneless app).
     */
    readonly selectedProgrammingLanguage = input<ProgrammingLanguage | undefined>(undefined);
    /**
     * The exercise's effective project type (e.g. MAVEN_MAVEN rather than the PLAIN_MAVEN the project type picker shows), passed explicitly for the same
     * reason as the language. It selects the template whose Ares version a new exercise starts with.
     */
    readonly projectType = input<ProjectType | undefined>(undefined);
    /** Create- and import-mode choice the parent form holds, passed back in so it survives this card being re-created. */
    readonly stagedChoice = input<SecurityFrameworkChoice | undefined>(undefined);
    /** Emits the choice the parent sends with its create or import request; undefined keeps the template's or the imported setting. */
    readonly stagedChoiceChange = output<SecurityFrameworkChoice | undefined>();
    /** True while the parent form is saving the exercise, so this card's controls stay locked - a staged change made mid-save would disagree with what gets persisted. */
    readonly isSaving = input<boolean>(false);

    readonly isEditMode = computed(() => this.mode() === 'edit');
    readonly isCreateMode = computed(() => this.mode() === 'create');
    readonly isImportMode = computed(() => this.mode() === 'import');
    readonly isSupportedLanguage = computed(() => {
        const language = this.selectedProgrammingLanguage() ?? this.programmingExercise().programmingLanguage;
        return language !== undefined && ProgrammingExerciseSecurityComponent.SUPPORTED_LANGUAGES.has(language);
    });
    /** The black-box template runs its tests without Ares, so there is nothing to activate. */
    readonly isSupportedProjectType = computed(() => (this.projectType() ?? this.programmingExercise().projectType) !== ProjectType.MAVEN_BLACKBOX);
    /** Whether the card is shown at all. */
    readonly isAvailable = computed(() => this.isSupportedLanguage() && this.isSupportedProjectType());

    /** The versions the server offers, or undefined while they load (or if loading failed). */
    readonly versions = signal<SecurityFrameworkVersions | undefined>(undefined);
    /** The Ares versions an instructor can pin, newest first. */
    readonly selectableVersions = computed(() => this.versions()?.selectableVersions ?? []);
    /** The newest selectable version, labeled "(latest)". */
    readonly latestVersion = computed(() => this.selectableVersions()[0]);
    /**
     * The version the tests repository template of the selected project type pins. A new exercise is created with exactly this version unless the
     * instructor picks another one. It may be no longer selectable on instances that override the templates.
     */
    readonly defaultVersion = computed(() => {
        const templateVersions = this.versions()?.templateVersions ?? {};
        const projectType = this.projectType() ?? this.programmingExercise().projectType;
        return (projectType ? templateVersions[projectType] : undefined) ?? Object.values(templateVersions)[0] ?? this.latestVersion();
    });
    /** The template's version if it is no longer selectable (create mode only); a new exercise then gets the newest selectable version instead. */
    readonly unsupportedTemplateVersion = computed(() => {
        const templateVersion = this.defaultVersion();
        if (!this.isCreateMode() || this.versions() === undefined || !templateVersion || this.selectableVersions().includes(templateVersion)) {
            return undefined;
        }
        return templateVersion;
    });
    /** The version a staged activation pins unless the instructor picks one: the template's version, or the newest selectable one if it is no longer selectable. */
    private readonly stagedDefaultVersion = computed(() => (this.unsupportedTemplateVersion() !== undefined ? this.latestVersion() : this.defaultVersion()));

    /** Dropdown options: the selectable versions, plus the version the exercise currently pins if it is no longer selectable. */
    readonly frameworkVersions = computed<SecurityFrameworkVersionOption[]>(() => {
        const selectable = this.selectableVersions();
        const options = selectable.map((version) => ({
            version,
            label: version === this.latestVersion() ? `${version} ${this.translateService.instant('artemisApp.programmingExercise.security.latestSuffix')}` : version,
        }));
        const current = this.config().frameworkVersion;
        if (current && !selectable.includes(current)) {
            options.push({ version: current, label: `${current} ${this.translateService.instant('artemisApp.programmingExercise.security.noLongerSupportedSuffix')}` });
        }
        return options;
    });

    /**
     * The one source of truth. Every derived value below reads from this. In edit mode it is the state of the repository (plus the
     * transient client states), in create and import mode the state the card stages. A new Java exercise starts ACTIVE, as its template does.
     */
    readonly config = signal<SecurityFrameworkConfig>({ status: SecurityActivationStatus.ACTIVE });
    readonly status = computed(() => this.config().status);
    /** The state of the source exercise of an import, as loaded; the staged {@link config} is compared with it to decide what to send. */
    readonly sourceConfig = signal<SecurityFrameworkConfig | undefined>(undefined);
    /** The three-way choice of an import whose state is unknown (from a file or the sharing platform) or whose source is MISCONFIGURED. */
    readonly importAction = signal<SecurityImportAction>('KEEP');
    /** True once the instructor picked a version in create mode, so a change of the project type no longer replaces it with the template's version. */
    private readonly versionPicked = signal(false);

    /** Whether the last *settled* state was active. Used so an ERROR (which can follow an activate or a deactivate) shows the toggle correctly. */
    private readonly lastSettledActive = signal(true);
    /** True while the deactivation confirmation is open, so the toggle shows the requested position until the instructor decides. */
    private readonly deactivationPending = signal(false);
    /** True when loading the persisted config failed, so the card shows a load-error + retry instead of a misleading INACTIVE state. */
    readonly loadFailed = signal(false);
    /** True while the persisted (edit) or source (import) config is loading. Keeps the controls disabled until it settles, so an early toggle cannot be overwritten by a late load. */
    readonly loading = signal(false);

    readonly isActive = computed(() => isSecurityActive(this.config()));
    /** In progress - loading the config, or activating/deactivating: spinner on, controls locked to prevent a double-submit or acting on not-yet-loaded state. */
    readonly isBusy = computed(() => this.loading() || TRANSIENT_SECURITY_STATUSES.has(this.status()));
    /** Import from a file or the sharing platform: the state is only known after the import, and a MISCONFIGURED source can only be kept, repaired or deactivated. */
    readonly usesImportActionChoice = computed(
        () => this.isImportMode() && (this.sourceExerciseId() === undefined || this.sourceConfig()?.status === SecurityActivationStatus.MISCONFIGURED),
    );
    readonly isUnsupported = computed(() => this.status() === SecurityActivationStatus.UNSUPPORTED);
    readonly isMisconfigured = computed(() => this.status() === SecurityActivationStatus.MISCONFIGURED);
    /** The on/off switch is replaced by the reasons (UNSUPPORTED), by Repair and Deactivate (MISCONFIGURED), or by the three-way choice. */
    readonly showToggle = computed(() => !this.usesImportActionChoice() && !this.isUnsupported() && !this.isMisconfigured());
    readonly showVersionSelect = computed(() => !this.usesImportActionChoice() && !this.isUnsupported());
    /**
     * The version selector is locked while a sync is in flight and while the card is in ERROR. A version picked in
     * ERROR would only change local state (no request is made unless the exercise is active), while Retry still
     * re-runs the previously failed operation for the old version - so the shown selection could silently diverge
     * from what Retry actually does.
     */
    readonly isVersionSelectDisabled = computed(
        () => this.isBusy() || this.isSaving() || this.status() === SecurityActivationStatus.ERROR || this.frameworkVersions().length === 0,
    );
    /** The toggle sits "on" whenever the sandbox is on or being turned on (or errored while on). */
    readonly isToggleOn = computed(() => {
        if (this.deactivationPending()) {
            return false;
        }
        const status = this.status();
        // On ERROR, reflect the last settled state: a failed activation stays off, a failed deactivation/version change stays on.
        if (status === SecurityActivationStatus.ERROR) {
            return this.lastSettledActive();
        }
        return status === SecurityActivationStatus.ACTIVE || status === SecurityActivationStatus.GENERATING;
    });

    /** Import from a file or the sharing platform: the state of the tests is only known after the import. */
    readonly isStateUnknown = computed(() => this.isImportMode() && this.sourceExerciseId() === undefined);
    /** Lowercase status name, used to key the state-based CSS class (sf-card--active, etc.) and the pill label. */
    readonly statusVariant = computed(() => (this.isStateUnknown() ? 'unknown' : this.status().toLowerCase()));
    /** Why the config is MISCONFIGURED or UNSUPPORTED, one translatable message per broken policy file or reason. */
    readonly problemMessages = computed(() => securityFrameworkProblemMessages(this.config()));
    /** Whether a broken policy file exists that activating cannot repair, so Repair is expected to fail. */
    readonly hasUnrepairableProblem = computed(() =>
        (this.config().policyFiles ?? []).some((file) => file.problem !== undefined && UNREPAIRABLE_POLICY_FILE_PROBLEMS.has(file.problem)),
    );
    /** The test classes an active policy file does not list; Ares treats them like student code. */
    readonly missingTestClasses = computed(() => (this.config().policyFiles ?? []).flatMap((file) => file.missingTestClasses ?? []));
    /** The policy files whose list of test classes could not be located, so they were not checked. */
    readonly uncheckedPolicyFiles = computed(() =>
        (this.config().policyFiles ?? []).filter((file) => file.problem === SecurityPolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND).map((file) => file.policyPath),
    );
    /** Options of the three-way import choice; the labels are translated in the template, so they follow a language change. */
    protected readonly importActionOptions: readonly { value: SecurityImportAction; labelKey: string }[] = [
        { value: 'KEEP', labelKey: 'artemisApp.programmingExercise.security.importAction.keep' },
        { value: SecurityFrameworkAction.ACTIVATE, labelKey: 'artemisApp.programmingExercise.security.importAction.activate' },
        { value: SecurityFrameworkAction.DEACTIVATE, labelKey: 'artemisApp.programmingExercise.security.importAction.deactivate' },
    ];

    /**
     * The choice the parent sends with its create or import request: what differs from the template (create) or from the source exercise (import).
     * Undefined when nothing differs, in edit mode, and while the state is unknown.
     */
    readonly choiceToSend = computed<SecurityFrameworkChoice | undefined>(() => {
        if (this.isEditMode()) {
            return undefined;
        }
        if (this.usesImportActionChoice()) {
            const action = this.importAction();
            return action === 'KEEP' ? undefined : { action };
        }
        const baseline = this.isCreateMode() ? { status: SecurityActivationStatus.ACTIVE, frameworkVersion: this.defaultVersion() } : this.sourceConfig();
        if (!baseline || baseline.status === SecurityActivationStatus.UNSUPPORTED) {
            return undefined;
        }
        const config = this.config();
        const version = config.frameworkVersion;
        const versionChanged = version !== undefined && version !== baseline.frameworkVersion;
        const baselineActive = isSecurityActive(baseline);
        if (config.status === SecurityActivationStatus.INACTIVE && baselineActive) {
            const choice: SecurityFrameworkChoice = { action: SecurityFrameworkAction.DEACTIVATE };
            // Creating writes the version even when deactivated, so a later activation pins it; deactivating an import ignores it.
            if (this.isCreateMode() && versionChanged) {
                choice.frameworkVersion = version;
            }
            return choice;
        }
        if (config.status === SecurityActivationStatus.ACTIVE && (!baselineActive || versionChanged)) {
            const choice: SecurityFrameworkChoice = { action: SecurityFrameworkAction.ACTIVATE };
            if (version !== undefined) {
                choice.frameworkVersion = version;
            }
            return choice;
        }
        return undefined;
    });

    /** Create and import mode ask for no confirmation; a staged deactivation only shows a short hint under the switch. */
    readonly showStagedDeactivationHint = computed(() => this.choiceToSend()?.action === SecurityFrameworkAction.DEACTIVATE);

    private readonly seeded = signal(false);
    /** A choice the parent held when this card was created in import mode, re-applied once the source config has loaded. */
    private pendingReseed?: SecurityFrameworkChoice;
    /** The source exercise whose config was last requested, so a late response for an earlier source is discarded. */
    private requestedSourceId?: number;
    /** The last sync operation started, kept so {@link onRetry} can re-run exactly what failed. */
    private pendingOperation?: { transientPatch: Partial<SecurityFrameworkConfig>; run: () => Observable<SecurityFrameworkConfig> };

    constructor() {
        this.securityService.getVersions().subscribe({
            next: (versions) => this.versions.set(versions),
            // Without versions the selector stays disabled; activation still works with the version the exercise pins.
            error: () => this.versions.set(undefined),
        });

        // Create mode: show the version the exercise will be created with, i.e. the template's version for the selected project type (or the newest
        // selectable one if the template's is no longer selectable), until the instructor picks one. It follows a change of the project type.
        effect(() => {
            const defaultVersion = this.stagedDefaultVersion();
            if (!defaultVersion || !this.isCreateMode() || this.versionPicked()) {
                return;
            }
            untracked(() => {
                if (this.config().frameworkVersion !== defaultVersion) {
                    this.patchConfig({ frameworkVersion: defaultVersion });
                }
            });
        });

        // Seed once the card is shown: a hidden card (another language, the black-box project type) must not read a repository.
        effect(() => {
            if (!this.isAvailable() || untracked(() => this.seeded())) {
                return;
            }
            untracked(() => this.seed());
        });

        // Import from an exercise: load the source's config, again whenever the source changes.
        effect(() => {
            const sourceId = this.sourceExerciseId();
            if (!this.seeded() || !this.isImportMode() || sourceId === undefined || sourceId === this.requestedSourceId) {
                return;
            }
            untracked(() => this.loadSourceConfig(sourceId));
        });

        // Create and import mode: hand the choice to the parent whenever it differs from what the parent holds, including after the parent cleared it.
        effect(() => {
            const choice = this.choiceToSend();
            const staged = this.stagedChoice();
            if (this.isEditMode() || !this.seeded() || !this.isAvailable() || isSameSecurityChoice(choice, staged)) {
                return;
            }
            untracked(() => this.stagedChoiceChange.emit(choice));
        });
    }

    private seed(): void {
        this.seeded.set(true);
        const staged = this.stagedChoice();
        if (this.isEditMode()) {
            const exerciseId = this.programmingExercise().id;
            if (exerciseId !== undefined) {
                this.loadConfig(exerciseId);
            }
        } else if (this.usesImportActionChoice()) {
            // Import from a file or the sharing platform: restore a choice made before this card was re-created.
            this.importAction.set(staged?.action ?? 'KEEP');
        } else if (this.isImportMode()) {
            this.pendingReseed = staged;
        } else if (staged) {
            // Create mode: restore a choice made before this card was re-created.
            this.applyStagedChoice(staged);
        }
    }

    private applyStagedChoice(choice: SecurityFrameworkChoice): void {
        const active = choice.action === SecurityFrameworkAction.ACTIVATE;
        this.patchConfig({ status: active ? SecurityActivationStatus.ACTIVE : SecurityActivationStatus.INACTIVE });
        if (choice.frameworkVersion) {
            this.patchConfig({ frameworkVersion: choice.frameworkVersion });
            this.versionPicked.set(true);
        }
        this.lastSettledActive.set(active);
    }

    /** Loads the persisted config (edit mode); a failure surfaces a distinct load-error state instead of a misleading INACTIVE. */
    private loadConfig(exerciseId: number): void {
        this.loadFailed.set(false);
        this.loading.set(true);
        this.securityService.getConfig(exerciseId).subscribe({
            next: (config) => {
                this.loading.set(false);
                // An operation started while the config was still loading owns the state now; a late load must not overwrite its result.
                if (this.pendingOperation) {
                    return;
                }
                this.config.set(config);
                this.lastSettledActive.set(isSecurityActive(config));
            },
            error: () => {
                this.loading.set(false);
                this.loadFailed.set(true);
            },
        });
    }

    /** Loads the config of the source exercise of an import, read-only. Changes are only staged on top of it. */
    private loadSourceConfig(sourceId: number): void {
        this.requestedSourceId = sourceId;
        this.sourceConfig.set(undefined);
        this.importAction.set('KEEP');
        this.loadFailed.set(false);
        this.loading.set(true);
        const isCurrent = () => this.requestedSourceId === sourceId && this.sourceExerciseId() === sourceId;
        this.securityService.getConfig(sourceId).subscribe({
            next: (config) => {
                if (!isCurrent()) {
                    return;
                }
                this.loading.set(false);
                this.sourceConfig.set(config);
                this.config.set(config);
                this.lastSettledActive.set(isSecurityActive(config));
                const reseed = this.pendingReseed;
                this.pendingReseed = undefined;
                if (reseed && this.usesImportActionChoice()) {
                    this.importAction.set(reseed.action);
                } else if (reseed && config.status !== SecurityActivationStatus.UNSUPPORTED) {
                    this.applyStagedChoice(reseed);
                }
            },
            error: () => {
                if (!isCurrent()) {
                    return;
                }
                this.loading.set(false);
                this.loadFailed.set(true);
            },
        });
    }

    /** Retry loading the persisted (edit) or source (import) config after a load failure. */
    retryLoadConfig(): void {
        if (this.isImportMode()) {
            const sourceId = this.sourceExerciseId();
            if (sourceId !== undefined) {
                this.loadSourceConfig(sourceId);
            }
            return;
        }
        const exerciseId = this.editExerciseId();
        if (exerciseId !== undefined) {
            this.loadConfig(exerciseId);
        }
    }

    /**
     * Create and import mode only stage ACTIVE/INACTIVE locally (sent with the create or import request). Edit mode runs the real
     * transient flow: GENERATING/DELETING immediately, settling to ACTIVE/INACTIVE when the server has committed the change.
     * Deactivating an existing exercise asks for confirmation first.
     */
    onToggleChanged(checked: boolean): void {
        const exerciseId = this.editExerciseId();
        if (exerciseId === undefined) {
            this.stageToggle(checked);
            return;
        }
        if (checked) {
            this.activate(exerciseId);
        } else {
            this.confirmDeactivation(exerciseId);
        }
    }

    /** MISCONFIGURED in edit mode: activating again writes the missing policy files with the selected version. */
    onRepair(): void {
        const exerciseId = this.editExerciseId();
        if (exerciseId !== undefined) {
            this.activate(exerciseId);
        }
    }

    /** MISCONFIGURED in edit mode: deactivating switches the policies off, after confirmation. */
    onDeactivate(): void {
        const exerciseId = this.editExerciseId();
        if (exerciseId !== undefined) {
            this.confirmDeactivation(exerciseId);
        }
    }

    /** The translation key of an option of the three-way import choice, which the select button passes untyped. */
    protected importActionLabelKey(option: unknown): string {
        return this.importActionOptions.find((candidate) => candidate === option)?.labelKey ?? '';
    }

    onImportActionChanged(action: unknown): void {
        if (action === 'KEEP' || action === SecurityFrameworkAction.ACTIVATE || action === SecurityFrameworkAction.DEACTIVATE) {
            this.importAction.set(action);
        }
    }

    /** Only an already-active exercise in edit mode has a repository to commit a version change to; otherwise the version is only staged. */
    onFrameworkVersionChanged(version: unknown): void {
        const newVersion = String(version);
        // Only selectable versions can be pinned; the version an exercise already pins is listed even if it no longer is, but cannot be chosen again.
        if (!this.selectableVersions().includes(newVersion)) {
            return;
        }
        const exerciseId = this.editExerciseId();
        if (exerciseId === undefined || !this.isActive()) {
            this.patchConfig({ frameworkVersion: newVersion });
            if (exerciseId === undefined) {
                this.versionPicked.set(true);
            }
            return;
        }
        this.runOperation({ status: SecurityActivationStatus.GENERATING, frameworkVersion: newVersion }, () => this.securityService.updateFrameworkVersion(exerciseId, newVersion));
    }

    /** Retry after an ERROR: re-run whichever sync operation failed (activation, deactivation or a version change). */
    onRetry(): void {
        if (this.pendingOperation && this.editExerciseId() !== undefined) {
            this.runOperation(this.pendingOperation.transientPatch, this.pendingOperation.run);
        }
    }

    /** The exercise whose repository the card may change: only in edit mode. During an import the exercise's id is the source's, which must never be written to. */
    private editExerciseId(): number | undefined {
        return this.isEditMode() ? this.programmingExercise().id : undefined;
    }

    private stageToggle(checked: boolean): void {
        if (checked) {
            this.patchConfig({ status: SecurityActivationStatus.ACTIVE, frameworkVersion: this.versionToActivate() });
        } else {
            this.patchConfig({ status: SecurityActivationStatus.INACTIVE });
        }
        this.lastSettledActive.set(checked);
    }

    /**
     * Keep the pinned version. Only once the versions are known and the pinned one is no longer selectable, use the template's version (or the newest
     * selectable one). While the versions are unknown (loading or failed), the server validates the pinned version, and a rejection shows as ERROR.
     */
    private versionToActivate(): string | undefined {
        const current = this.config().frameworkVersion;
        const versionsKnown = this.versions() !== undefined;
        return current && (!versionsKnown || this.selectableVersions().includes(current)) ? current : this.stagedDefaultVersion();
    }

    private activate(exerciseId: number): void {
        const version = this.versionToActivate();
        if (!version) {
            return;
        }
        this.runOperation({ status: SecurityActivationStatus.GENERATING, frameworkVersion: version }, () => this.securityService.activate(exerciseId, version));
    }

    /** Deactivating commits to the tests repository and runs the builds again, so the instructor confirms it; cancelling leaves everything as it was. */
    private confirmDeactivation(exerciseId: number): void {
        this.deactivationPending.set(true);
        this.confirmationService.confirm({
            key: DEACTIVATE_CONFIRMATION_KEY,
            header: this.translateService.instant('artemisApp.programmingExercise.security.deactivateConfirmation.header'),
            message: this.translateService.instant('artemisApp.programmingExercise.security.deactivateConfirmation.message'),
            acceptLabel: this.translateService.instant('artemisApp.programmingExercise.security.deactivateConfirmation.confirm'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptSeverity: 'danger',
            accept: () => {
                this.deactivationPending.set(false);
                this.runOperation({ status: SecurityActivationStatus.DELETING }, () => this.securityService.deactivate(exerciseId));
            },
            reject: () => this.deactivationPending.set(false),
        });
    }

    /**
     * The single place the component talks to the server: show the transient state immediately, then
     * settle to the returned config on success, or drop to ERROR on failure - keeping the failed
     * operation so the retry button re-runs exactly it. Defining the error contract here once means
     * every action (activate, repair, deactivate, version change) handles failure identically.
     */
    private runOperation(transientPatch: Partial<SecurityFrameworkConfig>, run: () => Observable<SecurityFrameworkConfig>): void {
        if (!this.isEditMode()) {
            return;
        }
        this.pendingOperation = { transientPatch, run };
        this.patchConfig(cloneWith(transientPatch, { errorDetail: undefined }));
        run().subscribe({
            next: (next) => {
                this.pendingOperation = undefined;
                this.settle(next);
            },
            error: (error: unknown) => {
                const errorDetail =
                    error instanceof HttpErrorResponse
                        ? (error.headers.get('X-artemisApp-message') ?? error.error?.title ?? error.error?.detail ?? error.error?.message ?? error.message)
                        : error instanceof Error
                          ? error.message
                          : String(error);
                this.patchConfig({ status: SecurityActivationStatus.ERROR, errorDetail });
            },
        });
    }

    private patchConfig(patch: Partial<SecurityFrameworkConfig>): void {
        this.config.set(cloneWith(this.config(), patch));
    }

    private settle(next: SecurityFrameworkConfig): void {
        // The server has committed this state to the tests repository; the returned config is the source of truth.
        this.config.set(next);
        this.lastSettledActive.set(isSecurityActive(next));
    }
}
