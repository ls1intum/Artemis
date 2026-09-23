import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumUiMessageComponent, TumUiSelectComponent, TumUiToggleSwitchComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faQuestionCircle, faRotateRight, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import {
    SecurityActivationStatus,
    SecurityFrameworkConfig,
    SecurityFrameworkVersionOption,
    SecurityStagedActivation,
    TRANSIENT_SECURITY_STATUSES,
    isSecurityActive,
} from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';

/**
 * "Activate Security Framework" card in the simple-mode create/edit form of a programming exercise
 * (Objective 1: automatic security policy generation on activation).
 *
 * Design principles:
 * - The card is rendered only for {@link SUPPORTED_LANGUAGES}; today just Java. Adding a language
 *   later is a one-line change here, nothing else - the rest of the component is language-agnostic.
 * - A single {@link SecurityActivationStatus} drives every visual (tint, pill, toggle, sync line,
 *   warning); the card can never show two contradictory states at once.
 * - It talks only to {@link SecurityFrameworkService} (a mock today). When the real server endpoints
 *   exist, only that service changes - this component does not.
 */
@Component({
    selector: 'jhi-programming-exercise-security',
    templateUrl: './programming-exercise-security.component.html',
    styleUrl: './programming-exercise-security.component.scss',
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiToggleSwitchComponent,
        TumUiSelectComponent,
        TumUiMessageComponent,
        TumUiTooltipDirective,
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
    /** Create-mode activation the parent form holds, passed back in so it survives a mode switch that destroys this card. */
    readonly stagedActivation = input<SecurityStagedActivation | undefined>(undefined);
    /** Emits the create-mode staged activation (or undefined when cleared) so the parent can persist it on save even if this card is gone. */
    readonly stagedActivationChange = output<SecurityStagedActivation | undefined>();

    readonly isEditMode = computed(() => !!this.programmingExercise().id);
    readonly isSupportedLanguage = computed(() => {
        const language = this.selectedProgrammingLanguage() ?? this.programmingExercise().programmingLanguage;
        return language !== undefined && ProgrammingExerciseSecurityComponent.SUPPORTED_LANGUAGES.has(language);
    });

    readonly frameworkVersions = computed<SecurityFrameworkVersionOption[]>(() =>
        this.securityService
            .getFrameworkVersions()
            .map((option) =>
                option.latest ? cloneWith(option, { label: `${option.version} ${this.translateService.instant('artemisApp.programmingExercise.security.latestSuffix')}` }) : option,
            ),
    );

    /** The one source of truth. Every derived value below reads from this. */
    readonly config = signal<SecurityFrameworkConfig>(this.securityService.getInitialConfigForCreate());
    readonly status = computed(() => this.config().status);

    /** Whether the last *settled* state was active. Used so an ERROR (which can follow an activate or a deactivate) shows the toggle correctly. */
    private readonly lastSettledActive = signal(false);
    /** True when loading the persisted config failed, so the card shows a load-error + retry instead of a misleading INACTIVE state. */
    readonly loadFailed = signal(false);

    readonly isActive = computed(() => isSecurityActive(this.config()));
    /** In progress (activating/deactivating): spinner on, controls locked to prevent double-submit. */
    readonly isBusy = computed(() => TRANSIENT_SECURITY_STATUSES.has(this.status()));
    /**
     * The version selector is locked while a sync is in flight and while the card is in ERROR. A version picked in
     * ERROR would only change local state (no request is made unless the exercise is active), while Retry still
     * re-runs the previously failed operation for the old version - so the shown selection could silently diverge
     * from what Retry actually does.
     */
    readonly isVersionSelectDisabled = computed(() => this.isBusy() || this.status() === SecurityActivationStatus.ERROR);
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
        // Seed once: an existing exercise loads its persisted config from the server; a not-yet-created
        // exercise keeps the local INACTIVE default (a create-mode activation is persisted by the parent
        // update component via commitStagedActivation() once the exercise has been created and has an id).
        effect(() => {
            const exerciseId = this.programmingExercise().id;
            if (this.hasSeededInitialConfig) {
                return;
            }
            this.hasSeededInitialConfig = true;
            if (exerciseId !== undefined) {
                this.loadConfig(exerciseId);
            } else {
                // Create mode: restore an activation staged before a simple <-> advanced mode switch destroyed this card.
                const staged = this.stagedActivation();
                if (staged) {
                    this.config.set({ status: SecurityActivationStatus.ACTIVE, frameworkVersion: staged.frameworkVersion });
                    this.lastSettledActive.set(true);
                }
            }
        });

        // Create mode only: the parent owns the staged activation. When it clears the staged value (e.g. the
        // instructor switches the exercise to a non-Java language, which hides but does not destroy this card),
        // reset the local state so switching back to Java cannot resurrect a staged ACTIVE that the parent will
        // not commit - otherwise the exercise would be created without the sandbox and with no warning.
        effect(() => {
            const staged = this.stagedActivation();
            if (this.programmingExercise().id !== undefined || staged) {
                return;
            }
            untracked(() => {
                if (this.config().status === SecurityActivationStatus.ACTIVE) {
                    this.patchConfig({ status: SecurityActivationStatus.INACTIVE });
                    this.lastSettledActive.set(false);
                }
            });
        });
    }

    /** Loads the persisted config; a failure surfaces a distinct load-error state instead of a misleading INACTIVE. */
    private loadConfig(exerciseId: number): void {
        this.loadFailed.set(false);
        this.securityService.getConfig(exerciseId).subscribe({
            next: (config) => {
                this.config.set(config);
                this.lastSettledActive.set(isSecurityActive(config));
            },
            error: () => this.loadFailed.set(true),
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
     * Create mode has no repository yet, so toggling only stages ACTIVE/INACTIVE locally (committed on
     * Generate). Edit mode runs the real transient flow: GENERATING/DELETING immediately, settling to
     * ACTIVE/INACTIVE when the (mock) server responds.
     */
    onToggleChanged(checked: boolean): void {
        const exerciseId = this.programmingExercise().id;
        if (exerciseId === undefined) {
            // Create mode: no exercise/repository yet, so stage locally (committed when the exercise is generated).
            this.patchConfig({ status: checked ? SecurityActivationStatus.ACTIVE : SecurityActivationStatus.INACTIVE });
            this.lastSettledActive.set(checked);
            this.stagedActivationChange.emit(checked ? { frameworkVersion: this.config().frameworkVersion } : undefined);
            return;
        }
        if (checked) {
            this.runOperation({ status: SecurityActivationStatus.GENERATING }, () => this.securityService.activate(exerciseId, this.config().frameworkVersion));
        } else {
            this.runOperation({ status: SecurityActivationStatus.DELETING }, () => this.securityService.deactivate(exerciseId));
        }
    }

    /** Only an already-active exercise in edit mode has a real policy to re-sync on a version change. */
    onFrameworkVersionChanged(version: unknown): void {
        const newVersion = String(version);
        const exerciseId = this.programmingExercise().id;
        if (exerciseId === undefined || !this.isActive()) {
            this.patchConfig({ frameworkVersion: newVersion });
            // Keep the parent's staged copy in sync when a create-mode activation is already staged.
            if (exerciseId === undefined && this.config().status === SecurityActivationStatus.ACTIVE) {
                this.stagedActivationChange.emit({ frameworkVersion: newVersion });
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
                this.patchConfig({ status: SecurityActivationStatus.ERROR, errorDetail: error instanceof Error ? error.message : String(error) });
            },
        });
    }

    private patchConfig(patch: Partial<SecurityFrameworkConfig>): void {
        this.config.set(cloneWith(this.config(), patch));
    }

    private settle(next: SecurityFrameworkConfig): void {
        // The server already persisted this state; the returned config is the source of truth.
        this.config.set(next);
        this.lastSettledActive.set(isSecurityActive(next));
    }
}
