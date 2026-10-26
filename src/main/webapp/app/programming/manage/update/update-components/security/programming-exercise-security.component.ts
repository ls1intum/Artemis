import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiMessageComponent, TumAetUiSelectComponent, TumAetUiToggleSwitchComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
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
    SecurityFrameworkConfig,
    SecurityFrameworkVersionOption,
    SecurityFrameworkVersions,
    SecurityStagedChoice,
    TRANSIENT_SECURITY_STATUSES,
    isSecurityActive,
} from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';

/**
 * "Activate Security Framework" card in the create/edit form of a programming exercise, shown in both
 * simple and advanced mode.
 *
 * Design principles:
 * - The card is rendered only for {@link SUPPORTED_LANGUAGES}; today just Java, whose templates ship with
 *   Ares enabled. Hence a new exercise starts out ACTIVE, and turning it off is the explicit choice.
 * - A single {@link SecurityActivationStatus} drives every visual (tint, pill, toggle, sync line,
 *   warning); the card can never show two contradictory states at once.
 * - It talks only to {@link SecurityFrameworkService}; the server commits every change to the tests repository.
 */
@Component({
    selector: 'jhi-programming-exercise-security',
    templateUrl: './programming-exercise-security.component.html',
    styleUrl: './programming-exercise-security.component.scss',
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiToggleSwitchComponent,
        TumAetUiSelectComponent,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        FaIconComponent,
    ],
})
export class ProgrammingExerciseSecurityComponent {
    /** Programming languages the Security Framework supports. Extend this to roll out to more. */
    private static readonly SUPPORTED_LANGUAGES: ReadonlySet<ProgrammingLanguage> = new Set([ProgrammingLanguage.JAVA]);

    private readonly securityService = inject(SecurityFrameworkService);
    private readonly translateService = inject(TranslateService);

    protected readonly SecurityActivationStatus = SecurityActivationStatus;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faRotateRight = faRotateRight;

    readonly programmingExercise = input.required<ProgrammingExercise>();
    /**
     * Selected language, passed as an explicit input from the wizard so the Java gate reacts to
     * language changes through Angular's binding (the parent mutates the exercise's language field in
     * place, which a read through the exercise input alone would not pick up in a zoneless app).
     */
    readonly selectedProgrammingLanguage = input<ProgrammingLanguage | undefined>(undefined);
    /** Selected project type, passed explicitly for the same reason as the language. */
    readonly selectedProjectType = input<ProjectType | undefined>(undefined);
    /** Create-mode choice the parent form holds, passed back in so it survives this card being re-created. */
    readonly stagedChoice = input<SecurityStagedChoice | undefined>(undefined);
    /** Emits the create-mode choice so the parent can apply it once the exercise has been created. */
    readonly stagedChoiceChange = output<SecurityStagedChoice>();
    /** True while the parent form is saving the exercise and running the follow-up activation, so this card's controls stay locked - a staged change made mid-save would disagree with what gets persisted. */
    readonly isSaving = input<boolean>(false);

    readonly isEditMode = computed(() => !!this.programmingExercise().id);
    readonly isSupportedLanguage = computed(() => {
        const language = this.selectedProgrammingLanguage() ?? this.programmingExercise().programmingLanguage;
        return language !== undefined && ProgrammingExerciseSecurityComponent.SUPPORTED_LANGUAGES.has(language);
    });
    /** The black-box template runs its tests without Ares, so there is nothing to activate. */
    readonly isSupportedProjectType = computed(() => (this.selectedProjectType() ?? this.programmingExercise().projectType) !== ProjectType.MAVEN_BLACKBOX);
    /** Whether the card is shown at all. */
    readonly isAvailable = computed(() => this.isSupportedLanguage() && this.isSupportedProjectType());

    /** The versions the server offers, or undefined while they load (or if loading failed). */
    readonly versions = signal<SecurityFrameworkVersions | undefined>(undefined);
    /** The Ares versions an instructor can pin, newest first. */
    readonly selectableVersions = computed(() => this.versions()?.selectableVersions ?? []);
    /** The newest selectable version, labeled "(latest)". */
    readonly latestVersion = computed(() => this.selectableVersions()[0]);
    /**
     * The version the tests repository template of the selected project type pins. A new exercise is created with exactly this version, so the create form
     * shows it until the instructor picks another one.
     */
    readonly defaultVersion = computed(() => {
        const templateVersions = this.versions()?.templateVersions ?? {};
        const projectType = this.selectedProjectType() ?? this.programmingExercise().projectType;
        return (projectType ? templateVersions[projectType] : undefined) ?? Object.values(templateVersions)[0] ?? this.latestVersion();
    });

    /** Dropdown options: the selectable versions, plus the version the exercise currently pins if it is no longer selectable. */
    readonly frameworkVersions = computed<SecurityFrameworkVersionOption[]>(() => {
        const versions = [...this.selectableVersions()];
        const current = this.config().frameworkVersion;
        if (current && !versions.includes(current)) {
            versions.push(current);
        }
        const latestSuffix = this.translateService.instant('artemisApp.programmingExercise.security.latestSuffix');
        return versions.map((version) => ({ version, label: version === this.latestVersion() ? `${version} ${latestSuffix}` : version }));
    });

    /** The one source of truth. Every derived value below reads from this. A new Java exercise starts ACTIVE, as its template does. */
    readonly config = signal<SecurityFrameworkConfig>({ status: SecurityActivationStatus.ACTIVE });
    readonly status = computed(() => this.config().status);

    /** Whether the last *settled* state was active. Used so an ERROR (which can follow an activate or a deactivate) shows the toggle correctly. */
    private readonly lastSettledActive = signal(true);
    /** True when loading the persisted config failed, so the card shows a load-error + retry instead of a misleading INACTIVE state. */
    readonly loadFailed = signal(false);
    /** True while the persisted config is loading (edit mode). Keeps the toggle and version selector disabled until it settles, so an early toggle cannot be overwritten by a late load. */
    readonly loading = signal(false);

    readonly isActive = computed(() => isSecurityActive(this.config()));
    /** In progress - loading the persisted config, or activating/deactivating: spinner on, controls locked to prevent a double-submit or acting on not-yet-loaded state. */
    readonly isBusy = computed(() => this.loading() || TRANSIENT_SECURITY_STATUSES.has(this.status()));
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
        const status = this.status();
        // On ERROR, reflect the last settled state: a failed activation stays off, a failed deactivation/version change stays on.
        if (status === SecurityActivationStatus.ERROR) {
            return this.lastSettledActive();
        }
        return status === SecurityActivationStatus.ACTIVE || status === SecurityActivationStatus.GENERATING;
    });

    /** Lowercase status name, used to key the state-based CSS class (sf-card--active, etc.). */
    readonly statusVariant = computed(() => this.status().toLowerCase());
    readonly showDeactivateWarning = computed(() => this.status() === SecurityActivationStatus.DELETING);

    private hasSeededInitialConfig = false;
    /** The last sync operation started, kept so {@link onRetry} can re-run exactly what failed. */
    private pendingOperation?: { transientPatch: Partial<SecurityFrameworkConfig>; run: () => Observable<SecurityFrameworkConfig> };

    constructor() {
        this.securityService.getVersions().subscribe({
            next: (versions) => this.versions.set(versions),
            // Without versions the selector stays disabled; activation still works with the version the exercise pins.
            error: () => this.versions.set(undefined),
        });

        // Create mode: show the version the exercise will be created with, i.e. the template's version for the selected project type, until the
        // instructor makes a choice. It follows a change of the project type, because the templates can pin different versions.
        effect(() => {
            const defaultVersion = this.defaultVersion();
            if (!defaultVersion || this.programmingExercise().id !== undefined || this.stagedChoice()) {
                return;
            }
            untracked(() => {
                if (this.config().frameworkVersion !== defaultVersion) {
                    this.patchConfig({ frameworkVersion: defaultVersion });
                }
            });
        });

        // Seed once: an existing exercise loads its config from the server; a not-yet-created exercise keeps the
        // template default (ACTIVE). The parent applies a create-mode choice once the exercise has been created.
        effect(() => {
            const exerciseId = this.programmingExercise().id;
            // Wait until the card is shown: a hidden card (another language, the black-box project type) must not read the repository.
            if (this.hasSeededInitialConfig || !this.isAvailable()) {
                return;
            }
            this.hasSeededInitialConfig = true;
            if (exerciseId !== undefined) {
                this.loadConfig(exerciseId);
            } else {
                // Create mode: restore a choice made before this card was re-created.
                const staged = untracked(() => this.stagedChoice());
                if (staged) {
                    this.config.set({ status: staged.active ? SecurityActivationStatus.ACTIVE : SecurityActivationStatus.INACTIVE, frameworkVersion: staged.frameworkVersion });
                    this.lastSettledActive.set(staged.active);
                }
            }
        });

        // Create mode only: the parent owns the staged choice. When it clears it (e.g. the instructor switches the
        // exercise to a non-Java language, which hides but does not destroy this card), go back to the template
        // default, so the card never shows a choice the parent will not apply.
        effect(() => {
            const staged = this.stagedChoice();
            if (this.programmingExercise().id !== undefined || staged) {
                return;
            }
            untracked(() => {
                this.config.set({ status: SecurityActivationStatus.ACTIVE, frameworkVersion: this.defaultVersion() });
                this.lastSettledActive.set(true);
            });
        });
    }

    /** Loads the persisted config; a failure surfaces a distinct load-error state instead of a misleading INACTIVE. */
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

    /** Retry loading the persisted config after a load failure. */
    retryLoadConfig(): void {
        const exerciseId = this.programmingExercise().id;
        if (exerciseId !== undefined) {
            this.loadConfig(exerciseId);
        }
    }

    /**
     * Create mode has no repository yet, so toggling only stages ACTIVE/INACTIVE locally (applied once the
     * exercise is created). Edit mode runs the real transient flow: GENERATING/DELETING immediately, settling to
     * ACTIVE/INACTIVE when the server has committed the change.
     */
    onToggleChanged(checked: boolean): void {
        const exerciseId = this.programmingExercise().id;
        if (exerciseId === undefined) {
            this.patchConfig({ status: checked ? SecurityActivationStatus.ACTIVE : SecurityActivationStatus.INACTIVE });
            this.lastSettledActive.set(checked);
            this.emitStagedChoice();
            return;
        }
        if (checked) {
            // An exercise that pins a version which is no longer selectable is activated with the template's version, which the server accepts.
            const current = this.config().frameworkVersion;
            const version = current && this.selectableVersions().includes(current) ? current : this.defaultVersion();
            if (!version) {
                return;
            }
            this.runOperation({ status: SecurityActivationStatus.GENERATING, frameworkVersion: version }, () => this.securityService.activate(exerciseId, version));
        } else {
            this.runOperation({ status: SecurityActivationStatus.DELETING }, () => this.securityService.deactivate(exerciseId));
        }
    }

    /** Only an already-active exercise in edit mode has a repository to commit a version change to. */
    onFrameworkVersionChanged(version: unknown): void {
        const newVersion = String(version);
        // Only selectable versions can be pinned; the version an exercise already pins is listed even if it no longer is, but cannot be chosen again.
        if (!this.selectableVersions().includes(newVersion)) {
            return;
        }
        const exerciseId = this.programmingExercise().id;
        if (exerciseId === undefined || !this.isActive()) {
            this.patchConfig({ frameworkVersion: newVersion });
            if (exerciseId === undefined) {
                this.emitStagedChoice();
            }
            return;
        }
        this.runOperation({ status: SecurityActivationStatus.GENERATING, frameworkVersion: newVersion }, () => this.securityService.updateFrameworkVersion(exerciseId, newVersion));
    }

    /** Retry after an ERROR: re-run whichever sync operation failed (activation, deactivation or a version change). */
    onRetry(): void {
        if (this.pendingOperation) {
            this.runOperation(this.pendingOperation.transientPatch, this.pendingOperation.run);
        }
    }

    /**
     * The single place the component talks to the server: show the transient state immediately, then
     * settle to the returned config on success, or drop to ERROR on failure - keeping the failed
     * operation so the retry button re-runs exactly it. Defining the error contract here once means
     * every action (activate, deactivate, version change) handles failure identically.
     */
    private runOperation(transientPatch: Partial<SecurityFrameworkConfig>, run: () => Observable<SecurityFrameworkConfig>): void {
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

    private emitStagedChoice(): void {
        const config = this.config();
        this.stagedChoiceChange.emit({ active: isSecurityActive(config), frameworkVersion: config.frameworkVersion ?? this.defaultVersion() });
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
