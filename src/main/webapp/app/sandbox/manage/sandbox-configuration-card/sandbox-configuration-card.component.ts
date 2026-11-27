import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiConfirmDialogComponent, TumAetUiConfirmationService } from '@tumaet/ui-angular/confirm-dialog';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { TumAetUiSelectComponent } from '@tumaet/ui-angular/select';
import { TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular/toggle-switch';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faQuestionCircle, faRotateRight, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { SandboxApi } from 'app/openapi/api/sandbox-api';
import { SandboxConfiguration } from 'app/openapi/model/sandbox-configuration';
import { SandboxFrameworkVersions } from 'app/openapi/model/sandbox-framework-versions';
import { SandboxVersionCompatibility } from 'app/openapi/model/sandbox-version-compatibility';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import {
    SandboxCardState,
    SandboxCardStatus,
    SandboxVersionOption,
    TRANSIENT_SANDBOX_STATUSES,
    UNREPAIRABLE_POLICY_FILE_PROBLEMS,
    VERSION_INDEPENDENT_POLICY_FILE_PROBLEMS,
    sandboxProblemMessages,
    toCardState,
} from 'app/sandbox/shared/entities/sandbox-card-state.model';

const DEACTIVATE_CONFIRMATION_KEY = 'sandboxDeactivateConfirmation';

/**
 * The sandbox card of an existing Java programming exercise, shown in the advanced mode of its edit form.
 *
 * - The exercise-tests repository is the source of truth. The card reads its configuration, and every change (activate, deactivate, another Ares version,
 *   adding missing test classes) is committed by the server right away, independently of saving the form.
 * - A single {@link SandboxCardStatus} drives every visual (tint, pill, toggle, sync line, warning), so the card never shows two contradictory states.
 * - Creating an exercise uses the template's setup and an import keeps the setup of its source, so the card is not part of those forms.
 */
@Component({
    selector: 'jhi-sandbox-configuration-card',
    templateUrl: './sandbox-configuration-card.component.html',
    styleUrl: './sandbox-configuration-card.component.scss',
    providers: [TumAetUiConfirmationService],
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiToggleSwitchComponent,
        TumAetUiSelectComponent,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        TumAetUiButtonDirective,
        TumAetUiConfirmDialogComponent,
        FaIconComponent,
    ],
})
export class SandboxConfigurationCardComponent {
    private readonly sandboxApi = inject(SandboxApi);
    private readonly translateService = inject(TranslateService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);

    protected readonly SandboxCardStatus = SandboxCardStatus;
    protected readonly DEACTIVATE_CONFIRMATION_KEY = DEACTIVATE_CONFIRMATION_KEY;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faRotateRight = faRotateRight;

    /** The exercise whose tests repository the card shows and changes; it must have been saved. */
    readonly programmingExercise = input.required<ProgrammingExercise>();

    /** Only Java templates integrate Ares, and the black-box template runs its tests without it, so the card is shown for the other Java exercises only. */
    readonly isAvailable = computed(() => {
        const exercise = this.programmingExercise();
        return exercise.id !== undefined && exercise.programmingLanguage === ProgrammingLanguage.JAVA && exercise.projectType !== ProjectType.MAVEN_BLACKBOX;
    });

    /** The versions the server offers, or undefined while they load (or if loading failed). */
    readonly versions = signal<SandboxFrameworkVersions | undefined>(undefined);
    /** The Ares versions an instructor can pin, newest first. */
    readonly selectableVersions = computed(() => this.versions()?.selectableVersions ?? []);
    /** The newest selectable version, labeled "(latest)". */
    readonly latestVersion = computed(() => this.selectableVersions()[0]);
    /**
     * The version an activation pins if the exercise pins none that is still selectable: the version the template of its project type starts with, or the
     * newest selectable one if that one is no longer selectable, because the server refuses to pin an unsupported version.
     */
    private readonly defaultVersion = computed(() => {
        const templateVersions = this.versions()?.templateVersions ?? {};
        const projectType = this.programmingExercise().projectType;
        const templateVersion = (projectType ? templateVersions[projectType] : undefined) ?? Object.values(templateVersions)[0];
        return templateVersion && this.selectableVersions().includes(templateVersion) ? templateVersion : this.latestVersion();
    });

    /** Whether each selectable version accepts the policy files of the exercise, by version; empty if the server checked no policy file. */
    private readonly versionCompatibilities = computed(
        () =>
            new Map<string, SandboxVersionCompatibility>(
                (this.state().versionCompatibilities ?? []).map((compatibility) => [compatibility.version ?? '', compatibility] as [string, SandboxVersionCompatibility]),
            ),
    );

    /** The selectable versions that do not accept the policy files, with what they do not accept. The server refuses to pin them. */
    readonly incompatibleVersions = computed(() => [...this.versionCompatibilities().values()].filter((compatibility) => compatibility.verdict === 'INCOMPATIBLE'));

    /** Dropdown options: the selectable versions, plus the version the exercise currently pins if it is no longer selectable. */
    readonly frameworkVersions = computed<SandboxVersionOption[]>(() => {
        const selectable = this.selectableVersions();
        const options: SandboxVersionOption[] = selectable.map((version) => {
            const suffixes: string[] = [];
            if (version === this.latestVersion()) {
                suffixes.push(this.translateService.instant('artemisApp.sandbox.latestSuffix'));
            }
            const verdict = this.versionCompatibilities().get(version)?.verdict;
            if (verdict === 'INCOMPATIBLE') {
                suffixes.push(this.translateService.instant('artemisApp.sandbox.incompatibleSuffix'));
            } else if (verdict === 'UNVERIFIED') {
                suffixes.push(this.translateService.instant('artemisApp.sandbox.unverifiedSuffix'));
            }
            return { version, label: [version, ...suffixes].join(' '), incompatible: verdict === 'INCOMPATIBLE' };
        });
        const current = this.state().frameworkVersion;
        if (current && !selectable.includes(current)) {
            options.push({ version: current, label: `${current} ${this.translateService.instant('artemisApp.sandbox.noLongerSupportedSuffix')}` });
        }
        return options;
    });

    /** The one source of truth of the card: the state of the tests repository plus the transient client states. Every value below derives from it. */
    readonly state = signal<SandboxCardState>({ status: SandboxCardStatus.CONFIGURED });
    readonly status = computed(() => this.state().status);

    /** Whether the last settled state was active, so an ERROR (which can follow an activation or a deactivation) shows the toggle in the right position. */
    private readonly lastSettledActive = signal(true);
    /** True while the deactivation confirmation is open, so the toggle shows the requested position until the instructor decides. */
    private readonly deactivationPending = signal(false);
    /** True when loading the configuration failed, so the card shows a load error and Retry instead of a misleading INACTIVE state. */
    readonly loadFailed = signal(false);
    /** True while the configuration loads. Keeps the controls disabled until it settles, so an early toggle cannot be overwritten by a late load. */
    readonly loading = signal(false);
    /** True after a change failed until it or another one runs, so Retry can re-run it, also when the card fell back to MISCONFIGURED. */
    readonly hasFailedOperation = signal(false);

    readonly isActive = computed(() => this.status() === SandboxCardStatus.CONFIGURED);
    /** Loading or a change in flight: the spinner shows and the controls are locked, so nothing acts on a state that is not loaded or settled yet. */
    readonly isBusy = computed(() => this.loading() || TRANSIENT_SANDBOX_STATUSES.has(this.status()));
    readonly isUnsupported = computed(() => this.status() === SandboxCardStatus.UNSUPPORTED);
    readonly isMisconfigured = computed(() => this.status() === SandboxCardStatus.MISCONFIGURED);
    /** The on/off switch is replaced by the reasons (UNSUPPORTED) or by Repair and Deactivate (MISCONFIGURED). */
    readonly showToggle = computed(() => !this.isUnsupported() && !this.isMisconfigured());
    readonly showVersionSelect = computed(() => !this.isUnsupported());
    /**
     * The version select is locked while a change is in flight, in ERROR (Retry re-runs the failed change with its own version, so a version picked now
     * would not be what happens), while the sandbox is inactive (only an active sandbox pins a version), and while a policy file has a problem that no
     * version resolves, because picking a version activates with it, which the server rejects for the same file.
     */
    readonly isVersionSelectDisabled = computed(
        () =>
            this.isBusy() ||
            this.status() === SandboxCardStatus.ERROR ||
            this.status() === SandboxCardStatus.INACTIVE ||
            this.frameworkVersions().length === 0 ||
            this.hasVersionIndependentProblem(),
    );
    /** The toggle is on whenever the sandbox is on or being turned on, and on ERROR it shows the last settled state. */
    readonly isToggleOn = computed(() => {
        if (this.deactivationPending()) {
            return false;
        }
        const status = this.status();
        if (status === SandboxCardStatus.ERROR) {
            return this.lastSettledActive();
        }
        return status === SandboxCardStatus.CONFIGURED || status === SandboxCardStatus.GENERATING;
    });

    /** Lowercase status name, which keys the state-based CSS class (sf-card--configured, etc.) and the pill label. */
    readonly statusVariant = computed(() => (this.loading() ? 'loading' : this.status().toLowerCase()));
    /** Why the sandbox is MISCONFIGURED or UNSUPPORTED, one translatable message per broken policy file or reason. */
    readonly problemMessages = computed(() => sandboxProblemMessages(this.state()));
    /** Whether a broken policy file exists that no version resolves, see {@link VERSION_INDEPENDENT_POLICY_FILE_PROBLEMS}. */
    private readonly hasVersionIndependentProblem = computed(() =>
        (this.state().policyFiles ?? []).some((file) => file.problem !== undefined && VERSION_INDEPENDENT_POLICY_FILE_PROBLEMS.has(file.problem)),
    );
    /** Whether a broken policy file exists that activating cannot repair, so Repair is disabled. */
    readonly hasUnrepairableProblem = computed(() =>
        (this.state().policyFiles ?? []).some((file) => file.problem !== undefined && UNREPAIRABLE_POLICY_FILE_PROBLEMS.has(file.problem)),
    );
    /** The test classes an active policy file does not list; Ares treats them like student code. */
    readonly missingTestClasses = computed(() => (this.state().policyFiles ?? []).flatMap((file) => file.missingTestClasses ?? []));
    /** The policy files whose list of test classes could not be located, so they were not checked. */
    readonly uncheckedPolicyFiles = computed(() =>
        (this.state().policyFiles ?? []).filter((file) => file.problem === 'TEST_CLASS_LIST_NOT_FOUND').map((file) => file.policyPath ?? ''),
    );

    /** The last change started, so {@link onRetry} can re-run exactly what failed, for the exercise it changes. */
    private pendingOperation?: { visit: number; transientPatch: Partial<SandboxCardState>; run: () => Observable<SandboxConfiguration> };
    /** The exercise whose configuration the card shows. */
    private loadedExerciseId?: number;
    /**
     * Counts the exercises the card has shown, including a return to an earlier one (A, B, A), so a late response of an earlier visit is discarded even if
     * it is for the same exercise.
     */
    private visit = 0;
    /** Counts the loads of the configuration, so a late load never overwrites a newer one. */
    private loadCount = 0;

    constructor() {
        // The route can reuse this card for another exercise. Drop everything that belongs to the previous one, including a failed change that Retry
        // would re-run, and load the configuration and the versions of the new one. A hidden card (another language, the black-box template) reads nothing.
        effect(() => {
            const exerciseId = this.programmingExercise().id;
            if (!this.isAvailable() || exerciseId === undefined || exerciseId === this.loadedExerciseId) {
                return;
            }
            untracked(() => this.switchToExercise(exerciseId));
        });
    }

    private switchToExercise(exerciseId: number): void {
        // A confirmation that is still open belongs to the previous exercise.
        if (this.deactivationPending()) {
            this.confirmationService.close(DEACTIVATE_CONFIRMATION_KEY);
            this.deactivationPending.set(false);
        }
        this.visit++;
        this.pendingOperation = undefined;
        this.hasFailedOperation.set(false);
        this.state.set({ status: SandboxCardStatus.CONFIGURED });
        this.lastSettledActive.set(true);
        this.loadVersions(exerciseId);
        this.loadConfiguration(exerciseId);
    }

    private loadVersions(exerciseId: number): void {
        this.versions.set(undefined);
        const visit = this.visit;
        this.sandboxApi.getFrameworkVersions(exerciseId).subscribe({
            next: (versions) => {
                if (visit === this.visit) {
                    this.versions.set(versions);
                }
            },
            // Without versions the select stays disabled; activating still works with the version the exercise pins.
            error: () => undefined,
        });
    }

    /** Loads the configuration; a failure shows a load error instead of a misleading INACTIVE state. */
    private loadConfiguration(exerciseId: number): void {
        this.loadedExerciseId = exerciseId;
        const load = ++this.loadCount;
        this.loadFailed.set(false);
        this.loading.set(true);
        this.sandboxApi.getConfiguration(exerciseId).subscribe({
            next: (configuration) => {
                if (load !== this.loadCount) {
                    return;
                }
                this.loading.set(false);
                // A change started while the configuration was still loading owns the state now; a late load must not overwrite its result.
                if (this.pendingOperation) {
                    return;
                }
                this.settle(configuration);
            },
            error: () => {
                if (load !== this.loadCount) {
                    return;
                }
                this.loading.set(false);
                this.loadFailed.set(true);
            },
        });
    }

    /** Retry loading the configuration after a load failure. */
    retryLoadConfiguration(): void {
        const exerciseId = this.programmingExercise().id;
        if (exerciseId !== undefined) {
            this.loadConfiguration(exerciseId);
        }
    }

    /** Activating runs right away; deactivating asks for confirmation first. Both settle to the state the server committed. */
    onToggleChanged(checked: boolean): void {
        if (checked) {
            this.activate();
        } else {
            this.confirmDeactivation();
        }
    }

    /** CONFIGURED: adds the test classes that point to a policy file but are missing from it, so Ares does not treat them like student code. */
    onAddMissingTestClasses(): void {
        const exerciseId = this.programmingExercise().id;
        if (exerciseId !== undefined) {
            this.runOperation({ status: SandboxCardStatus.GENERATING }, () => this.sandboxApi.addMissingTestClasses(exerciseId));
        }
    }

    /** MISCONFIGURED: activating again writes the missing policy files with the selected version. */
    onRepair(): void {
        this.activate();
    }

    /** MISCONFIGURED: deactivating switches the policies off, after confirmation. */
    onDeactivate(): void {
        this.confirmDeactivation();
    }

    /** An active exercise pins the new version right away; a misconfigured one is repaired with it. */
    onFrameworkVersionChanged(version: unknown): void {
        const newVersion = String(version);
        const exerciseId = this.programmingExercise().id;
        // Only selectable versions can be pinned. A version that does not accept the policy files is listed so the instructor sees why, but the select does
        // not let it be chosen and the server would refuse it; this check only guards a programmatic call.
        if (exerciseId === undefined || !this.selectableVersions().includes(newVersion) || this.isIncompatibleVersion(newVersion)) {
            return;
        }
        if (this.isMisconfigured()) {
            // Repairing activates with the chosen version, which checks the policy against that version: a policy that only the pinned version refuses
            // becomes usable, and Repair may be disabled exactly then, so a version picked here must not stay a local, unsaved choice.
            this.runOperation({ status: SandboxCardStatus.GENERATING, frameworkVersion: newVersion }, () => this.sandboxApi.activate(exerciseId, { frameworkVersion: newVersion }));
        } else if (this.isActive()) {
            this.runOperation({ status: SandboxCardStatus.GENERATING, frameworkVersion: newVersion }, () =>
                this.sandboxApi.updateFrameworkVersion(exerciseId, { frameworkVersion: newVersion }),
            );
        }
    }

    /** Retry after an ERROR: re-run whichever change failed (activation, deactivation, a version change or adding test classes). */
    onRetry(): void {
        if (this.pendingOperation?.visit === this.visit) {
            this.runOperation(this.pendingOperation.transientPatch, this.pendingOperation.run);
        }
    }

    /**
     * Keep the pinned version. Only once the versions are known and the pinned one is no longer selectable, use the default version. While the versions
     * are unknown (loading or failed), the server validates the pinned version, and a rejection shows as ERROR.
     */
    private versionToActivate(): string | undefined {
        const current = this.state().frameworkVersion;
        const versionsKnown = this.versions() !== undefined;
        return current && (!versionsKnown || this.selectableVersions().includes(current)) ? current : this.defaultVersion();
    }

    private activate(): void {
        const exerciseId = this.programmingExercise().id;
        const version = this.versionToActivate();
        if (exerciseId === undefined || !version) {
            return;
        }
        this.runOperation({ status: SandboxCardStatus.GENERATING, frameworkVersion: version }, () => this.sandboxApi.activate(exerciseId, { frameworkVersion: version }));
    }

    /**
     * The confirmation text. If the tests use other parts of Ares, it also names them, because the Ares library then has to stay in the build: the sandbox
     * is switched off, but those tests still need the library.
     */
    private deactivationMessage(): string {
        const message = this.translateService.instant('artemisApp.sandbox.deactivateConfirmation.message');
        const apis = [...new Set((this.state().aresApiUses ?? []).map((use) => use.api))];
        if (apis.length === 0) {
            return message;
        }
        const library = this.translateService.instant('artemisApp.sandbox.deactivateConfirmation.aresLibraryStays', { apis: apis.join(', ') });
        return `${message} ${library}`;
    }

    /** Deactivating commits to the tests repository and runs the builds again, so the instructor confirms it; cancelling leaves everything as it was. */
    private confirmDeactivation(): void {
        const exerciseId = this.programmingExercise().id;
        if (exerciseId === undefined) {
            return;
        }
        const visit = this.visit;
        this.deactivationPending.set(true);
        this.confirmationService.confirm({
            key: DEACTIVATE_CONFIRMATION_KEY,
            header: this.translateService.instant('artemisApp.sandbox.deactivateConfirmation.header'),
            message: this.deactivationMessage(),
            acceptLabel: this.translateService.instant('artemisApp.sandbox.deactivateConfirmation.confirm'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptSeverity: 'danger',
            accept: () => {
                this.deactivationPending.set(false);
                // The card may have been reused for another exercise while the confirmation was open; never deactivate the previous one.
                if (visit !== this.visit) {
                    return;
                }
                this.runOperation({ status: SandboxCardStatus.DELETING }, () => this.sandboxApi.deactivate(exerciseId));
            },
            reject: () => this.deactivationPending.set(false),
        });
    }

    /**
     * The single place the card sends a change: show the transient state immediately, then settle to the returned configuration on success, or drop to
     * ERROR on failure, keeping the failed change so Retry re-runs exactly it. A failed change of a MISCONFIGURED exercise leaves it misconfigured, so Repair
     * and Deactivate stay available. Retry is offered too: the server may have pushed a repair with another version before it failed, which Repair, with the
     * restored version, would change again.
     */
    private runOperation(transientPatch: Partial<SandboxCardState>, run: () => Observable<SandboxConfiguration>): void {
        const visit = this.visit;
        const statusOnFailure = this.isMisconfigured() ? SandboxCardStatus.MISCONFIGURED : SandboxCardStatus.ERROR;
        // A misconfigured exercise falls back to its settled state on failure, which includes the version the repository still pins.
        const versionBefore = this.state().frameworkVersion;
        this.pendingOperation = { visit, transientPatch, run };
        this.hasFailedOperation.set(false);
        this.patchState(cloneWith(transientPatch, { errorDetail: undefined }));
        run().subscribe({
            next: (configuration) => {
                // The card was reused for another exercise while this ran; its state belongs to that visit now.
                if (visit !== this.visit) {
                    return;
                }
                this.pendingOperation = undefined;
                this.settle(configuration);
            },
            error: (error: unknown) => {
                if (visit !== this.visit) {
                    return;
                }
                const errorDetail = this.errorDetailOf(error);
                this.hasFailedOperation.set(true);
                if (statusOnFailure === SandboxCardStatus.MISCONFIGURED) {
                    this.patchState({ status: statusOnFailure, errorDetail, frameworkVersion: versionBefore });
                } else {
                    this.patchState({ status: statusOnFailure, errorDetail });
                }
            },
        });
    }

    /** The message shown for a failed change: the server's translated error if it sent one, otherwise the most specific text the error carries. */
    private errorDetailOf(error: unknown): string {
        if (error instanceof HttpErrorResponse) {
            return (
                this.translatedServerError(error) ?? error.headers.get('X-artemisApp-message') ?? error.error?.title ?? error.error?.detail ?? error.error?.message ?? error.message
            );
        }
        if (error instanceof Error) {
            return error.message;
        }
        return String(error);
    }

    private isIncompatibleVersion(version: string): boolean {
        return this.versionCompatibilities().get(version)?.verdict === 'INCOMPATIBLE';
    }

    /**
     * The server names the translation of its error in the message field, and its parameters in the params field; undefined if the translations do not
     * contain it, and the English text is shown instead.
     */
    private translatedServerError(error: HttpErrorResponse): string | undefined {
        const translationKey = error.error?.message;
        if (typeof translationKey !== 'string' || !translationKey.startsWith('error.')) {
            return undefined;
        }
        const params = error.error?.params;
        const translated = this.translateService.instant(translationKey, typeof params === 'object' && params !== null ? params : undefined);
        return translated.startsWith(translationNotFoundMessage) ? undefined : translated;
    }

    private patchState(patch: Partial<SandboxCardState>): void {
        this.state.set(cloneWith(this.state(), patch));
    }

    private settle(configuration: SandboxConfiguration): void {
        // The server has committed this state to the tests repository; the returned configuration is the source of truth.
        const state = toCardState(configuration);
        this.state.set(state);
        this.lastSettledActive.set(state.status === SandboxCardStatus.CONFIGURED);
    }
}
