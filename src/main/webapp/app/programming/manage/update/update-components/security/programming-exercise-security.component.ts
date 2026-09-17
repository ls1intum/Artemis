import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumUiMessageComponent, TumUiSelectComponent, TumUiToggleSwitchComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faQuestionCircle, faRotateRight, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Observable, catchError, of } from 'rxjs';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { SecurityActivationStatus, SecurityFrameworkConfig, TRANSIENT_SECURITY_STATUSES, isSecurityActive } from 'app/programming/shared/entities/security-framework-config.model';
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

    readonly isEditMode = computed(() => !!this.programmingExercise().id);
    readonly isSupportedLanguage = computed(() => {
        const language = this.selectedProgrammingLanguage() ?? this.programmingExercise().programmingLanguage;
        return language !== undefined && ProgrammingExerciseSecurityComponent.SUPPORTED_LANGUAGES.has(language);
    });

    readonly frameworkVersions = this.securityService.getFrameworkVersions();

    /** The one source of truth. Every derived value below reads from this. */
    readonly config = signal<SecurityFrameworkConfig>(this.securityService.getInitialConfigForCreate());
    readonly status = computed(() => this.config().status);

    readonly isActive = computed(() => isSecurityActive(this.config()));
    /** In progress (activating/deactivating): spinner on, controls locked to prevent double-submit. */
    readonly isBusy = computed(() => TRANSIENT_SECURITY_STATUSES.has(this.status()));
    /** The toggle sits "on" whenever the sandbox is on or being turned on (or errored while on). */
    readonly isToggleOn = computed(() => [SecurityActivationStatus.ACTIVE, SecurityActivationStatus.GENERATING, SecurityActivationStatus.ERROR].includes(this.status()));

    /** Lowercase status name, used to key the state-based CSS class (sf-card--active, etc.). */
    readonly statusVariant = computed(() => this.status().toLowerCase());
    readonly showDeactivateWarning = computed(() => this.status() === SecurityActivationStatus.DELETING);

    private hasSeededInitialConfig = false;
    /** True when the component first loaded before the exercise existed (create mode), so a staged activation still needs persisting on save. */
    private seededWithoutExercise = false;
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
            this.seededWithoutExercise = exerciseId === undefined;
            if (exerciseId !== undefined) {
                this.securityService.getConfig(exerciseId).subscribe({
                    next: (config) => this.config.set(config),
                    // Leave the default INACTIVE config if the state cannot be loaded; the instructor can still activate.
                    error: () => {},
                });
            }
        });
    }

    /**
     * Create mode has no repository yet, so toggling only stages ACTIVE/INACTIVE locally (committed on
     * Generate). Edit mode runs the real transient flow: GENERATING/DELETING immediately, settling to
     * ACTIVE/INACTIVE when the (mock) backend responds.
     */
    onToggleChanged(checked: boolean): void {
        const exerciseId = this.programmingExercise().id;
        if (exerciseId === undefined) {
            // Create mode: no exercise/repository yet, so stage locally (committed when the exercise is generated).
            this.patchConfig({ status: checked ? SecurityActivationStatus.ACTIVE : SecurityActivationStatus.INACTIVE });
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
     * The single place the component talks to the backend: show the transient state immediately, then
     * settle to the returned config on success, or drop to ERROR on failure - keeping the failed
     * operation so the retry button re-runs exactly it. Defining the error contract here once means
     * every action (activate, deactivate, version change) handles failure identically.
     */
    private runOperation(transientPatch: Partial<SecurityFrameworkConfig>, run: () => Observable<SecurityFrameworkConfig>): void {
        this.pendingOperation = { transientPatch, run };
        this.patchConfig({ ...transientPatch, errorDetail: undefined });
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

    /**
     * Persists a create-mode activation once the exercise exists. In create mode, toggling only stages
     * ACTIVE locally (there is no exercise/repository yet); the parent update component calls this after
     * the exercise is created so the staged activation is committed on the server. No-op in edit mode
     * (activation already went through the server) or when nothing was staged. Errors are swallowed so
     * saving and navigation still proceed - the instructor can activate again from the edit page.
     */
    commitStagedActivation(exerciseId: number | undefined): Observable<unknown> {
        if (!this.seededWithoutExercise || exerciseId === undefined || this.config().status !== SecurityActivationStatus.ACTIVE) {
            return of(undefined);
        }
        return this.securityService.activate(exerciseId, this.config().frameworkVersion).pipe(catchError(() => of(undefined)));
    }

    private patchConfig(patch: Partial<SecurityFrameworkConfig>): void {
        this.config.set({ ...deepClone(this.config()), ...patch });
    }

    private settle(next: SecurityFrameworkConfig): void {
        // The server already persisted this state; the returned config is the source of truth.
        this.config.set(next);
    }
}
