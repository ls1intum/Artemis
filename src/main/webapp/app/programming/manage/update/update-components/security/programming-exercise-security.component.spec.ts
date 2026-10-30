import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiConfirmationRequest, TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import {
    SecurityActivationStatus,
    SecurityFrameworkAction,
    SecurityFrameworkChoice,
    SecurityFrameworkConfig,
    SecurityFrameworkVersions,
    SecurityPolicyFileProblem,
    SecurityUnsupportedReasonType,
} from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
import { ProgrammingExerciseSecurityComponent } from 'app/programming/manage/update/update-components/security/programming-exercise-security.component';

const VERSIONS: SecurityFrameworkVersions = {
    selectableVersions: ['3.4.1', '3.3.0', '3.2.2'],
    templateVersions: { [ProjectType.PLAIN_GRADLE]: '3.2.2', [ProjectType.PLAIN_MAVEN]: '3.3.0', [ProjectType.MAVEN_MAVEN]: '3.4.1' },
};

const inactiveConfig = (): SecurityFrameworkConfig => ({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '3.4.1' });
const activeConfig = (version = '3.4.1'): SecurityFrameworkConfig => ({ status: SecurityActivationStatus.ACTIVE, frameworkVersion: version });
const misconfiguredConfig = (problem = SecurityPolicyFileProblem.MISSING): SecurityFrameworkConfig => ({
    status: SecurityActivationStatus.MISCONFIGURED,
    frameworkVersion: '3.3.0',
    policyFiles: [{ policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.ATest', 'de.x.BTest'], problem }],
});
const unsupportedConfig = (): SecurityFrameworkConfig => ({
    status: SecurityActivationStatus.UNSUPPORTED,
    unsupportedReasons: [
        { location: 'build.gradle', type: SecurityUnsupportedReasonType.ARES_1 },
        { location: 'build.gradle', type: SecurityUnsupportedReasonType.NO_ARES_VERSION_PINNED },
    ],
});

type SecurityServiceMock = {
    getVersions: ReturnType<typeof vi.fn>;
    getConfig: ReturnType<typeof vi.fn>;
    activate: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
    updateFrameworkVersion: ReturnType<typeof vi.fn>;
};

const createServiceMock = (): SecurityServiceMock => ({
    getVersions: vi.fn().mockReturnValue(of(VERSIONS)),
    getConfig: vi.fn().mockReturnValue(of(inactiveConfig())),
    activate: vi.fn(),
    deactivate: vi.fn(),
    updateFrameworkVersion: vi.fn(),
});

const javaExercise = (id?: number, projectType = ProjectType.PLAIN_GRADLE): ProgrammingExercise => {
    const exercise = new ProgrammingExercise(undefined, undefined);
    exercise.id = id;
    exercise.programmingLanguage = ProgrammingLanguage.JAVA;
    exercise.projectType = projectType;
    return exercise;
};

/**
 * Logic-focused spec: the component's status machine and its single server contract (getConfig /
 * activate / deactivate / updateFrameworkVersion), driven through a fully mocked
 * {@link SecurityFrameworkService}. The template is overridden to empty so the transitions are asserted
 * on the component's own signals, independent of the design-system child components.
 */
describe('ProgrammingExerciseSecurityComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseSecurityComponent>;
    let comp: ProgrammingExerciseSecurityComponent;
    let service: SecurityServiceMock;
    let emitted: (SecurityFrameworkChoice | undefined)[];

    beforeEach(() => {
        service = createServiceMock();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), { provide: TranslateService, useClass: MockTranslateService }, { provide: SecurityFrameworkService, useValue: service }],
        });
        TestBed.overrideComponent(ProgrammingExerciseSecurityComponent, { set: { template: '<div></div>' } });
        createComponent();
    });

    function createComponent(): void {
        fixture = TestBed.createComponent(ProgrammingExerciseSecurityComponent);
        comp = fixture.componentInstance;
        emitted = [];
        // Mirror the parent: it stores every emitted choice and passes it back in.
        comp.stagedChoiceChange.subscribe((choice) => {
            emitted.push(choice);
            fixture.componentRef.setInput('stagedChoice', choice);
        });
    }

    /** The confirmation service the card provides for its own dialog. */
    function confirmationService(): TumAetUiConfirmationService {
        return fixture.debugElement.injector.get(TumAetUiConfirmationService);
    }

    /** Captures the next confirmation request instead of opening the dialog. */
    function captureConfirmation(): () => TumAetUiConfirmationRequest {
        let request: TumAetUiConfirmationRequest | undefined;
        vi.spyOn(confirmationService(), 'confirm').mockImplementation((value) => (request = value));
        return () => {
            expect(request).toBeDefined();
            return request!;
        };
    }

    /** Deactivates in edit mode and confirms the dialog. */
    function deactivateConfirmed(): void {
        const request = captureConfirmation();
        comp.onToggleChanged(false);
        request().accept();
    }

    function initCreate(language = ProgrammingLanguage.JAVA): void {
        const exercise = javaExercise();
        exercise.programmingLanguage = language;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('selectedProgrammingLanguage', language);
        fixture.detectChanges();
    }

    function initEdit(initial: SecurityFrameworkConfig, language = ProgrammingLanguage.JAVA): void {
        service.getConfig.mockReturnValue(of(initial));
        const exercise = javaExercise(42);
        exercise.programmingLanguage = language;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('mode', 'edit');
        fixture.componentRef.setInput('selectedProgrammingLanguage', language);
        fixture.detectChanges();
    }

    /** Import from an exercise: the form's exercise carries the id of the source. */
    function initImportFromExercise(source: SecurityFrameworkConfig, sourceId = 7): void {
        service.getConfig.mockReturnValue(of(source));
        fixture.componentRef.setInput('programmingExercise', javaExercise(sourceId));
        fixture.componentRef.setInput('mode', 'import');
        fixture.componentRef.setInput('sourceExerciseId', sourceId);
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();
    }

    /** Import from a file or the sharing platform: no source exercise, the state is unknown. */
    function initImportWithUnknownState(): void {
        fixture.componentRef.setInput('programmingExercise', javaExercise());
        fixture.componentRef.setInput('mode', 'import');
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();
    }

    function expectNoWriteRequest(): void {
        expect(service.activate).not.toHaveBeenCalled();
        expect(service.deactivate).not.toHaveBeenCalled();
        expect(service.updateFrameworkVersion).not.toHaveBeenCalled();
    }

    it('shows the card only for supported languages (Java)', () => {
        initCreate(ProgrammingLanguage.JAVA);
        expect(comp.isSupportedLanguage()).toBe(true);
    });

    it('hides the card for the black-box project type, which runs its tests without Ares', () => {
        initCreate(ProgrammingLanguage.JAVA);
        fixture.componentRef.setInput('projectType', ProjectType.MAVEN_BLACKBOX);
        fixture.detectChanges();
        expect(comp.isAvailable()).toBe(false);

        fixture.componentRef.setInput('projectType', ProjectType.PLAIN_GRADLE);
        fixture.detectChanges();
        expect(comp.isAvailable()).toBe(true);
    });

    it('does not load the config while the card is hidden, and loads it once the card becomes available', () => {
        fixture.componentRef.setInput('programmingExercise', javaExercise(42, ProjectType.MAVEN_BLACKBOX));
        fixture.componentRef.setInput('mode', 'edit');
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.componentRef.setInput('projectType', ProjectType.MAVEN_BLACKBOX);
        fixture.detectChanges();
        expect(service.getConfig).not.toHaveBeenCalled();

        fixture.componentRef.setInput('projectType', ProjectType.PLAIN_GRADLE);
        fixture.detectChanges();
        expect(service.getConfig).toHaveBeenCalledOnce();
        expect(service.getConfig).toHaveBeenCalledWith(42);
    });

    it('hides the card for unsupported languages', () => {
        initCreate(ProgrammingLanguage.C);
        expect(comp.isSupportedLanguage()).toBe(false);
    });

    it('reacts to a language change away from Java', () => {
        initCreate(ProgrammingLanguage.JAVA);
        expect(comp.isSupportedLanguage()).toBe(true);
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.C);
        fixture.detectChanges();
        expect(comp.isSupportedLanguage()).toBe(false);
    });

    it('loads the persisted config from the server in edit mode', () => {
        initEdit(activeConfig('3.3.0'));
        expect(service.getConfig).toHaveBeenCalledWith(42);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.config().frameworkVersion).toBe('3.3.0');
    });

    it('create mode starts ACTIVE with the template version, like the Java template', () => {
        initCreate();
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.isToggleOn()).toBe(true);
        expect(comp.config().frameworkVersion).toBe('3.2.2');
    });

    it('shows the version of the template in create mode, not the newest selectable one, so nothing differs from what gets saved', () => {
        initCreate();
        expect(comp.latestVersion()).toBe('3.4.1');
        expect(comp.config().frameworkVersion).toBe('3.2.2');
        expect(comp.choiceToSend()).toBeUndefined();
        expect(emitted).toEqual([]);
    });

    it('follows the template version of the selected project type until the instructor picks a version', () => {
        initCreate();
        fixture.componentRef.setInput('projectType', ProjectType.PLAIN_MAVEN);
        fixture.detectChanges();
        expect(comp.config().frameworkVersion).toBe('3.3.0');

        comp.onFrameworkVersionChanged('3.4.1');
        fixture.componentRef.setInput('projectType', ProjectType.PLAIN_GRADLE);
        fixture.detectChanges();
        expect(comp.config().frameworkVersion).toBe('3.4.1');
    });

    it('preselects and sends the newest selectable version when the template version is no longer supported', () => {
        service.getVersions.mockReturnValue(of({ selectableVersions: ['3.4.1', '3.3.0'], templateVersions: { [ProjectType.PLAIN_GRADLE]: '2.0.0' } }));
        fixture.destroy();
        createComponent();
        initCreate();

        expect(comp.unsupportedTemplateVersion()).toBe('2.0.0');
        expect(comp.config().frameworkVersion).toBe('3.4.1');
        expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.ACTIVATE, frameworkVersion: '3.4.1' });
    });

    it('activates with the pinned version while the versions are unknown', () => {
        service.getVersions.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        // The versions are requested on construction, so re-create the component with the failing request.
        fixture.destroy();
        createComponent();
        initEdit({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '2.0.0' });
        service.activate.mockReturnValue(of(activeConfig('2.0.0')));

        comp.onToggleChanged(true);

        expect(service.activate).toHaveBeenCalledWith(42, '2.0.0');
    });

    it('ignores choosing a version that is not selectable', () => {
        initEdit(activeConfig('2.0.0'));
        comp.onFrameworkVersionChanged('2.0.0');
        expect(service.updateFrameworkVersion).not.toHaveBeenCalled();
    });

    it('uses the template of the effective Maven project type, which differs with dependencies', () => {
        initCreate();
        fixture.componentRef.setInput('projectType', ProjectType.MAVEN_MAVEN);
        fixture.detectChanges();
        expect(comp.config().frameworkVersion).toBe('3.4.1');

        fixture.componentRef.setInput('projectType', ProjectType.PLAIN_MAVEN);
        fixture.detectChanges();
        expect(comp.config().frameworkVersion).toBe('3.3.0');
    });

    it('activates an exercise that pins a no longer selectable version with the template version', () => {
        initEdit({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '2.0.0' });
        service.activate.mockReturnValue(of(activeConfig('3.2.2')));
        comp.onToggleChanged(true);
        expect(service.activate).toHaveBeenCalledWith(42, '3.2.2');
    });

    it('labels the newest version and marks a no longer supported version the exercise pins', () => {
        initEdit(activeConfig('2.0.0'));
        expect(comp.frameworkVersions().map((option) => option.version)).toEqual(['3.4.1', '3.3.0', '3.2.2', '2.0.0']);
        expect(comp.frameworkVersions()[0].label).toBe('3.4.1 artemisApp.programmingExercise.security.latestSuffix');
        expect(comp.frameworkVersions()[1].label).toBe('3.3.0');
        expect(comp.frameworkVersions()[3].label).toBe('2.0.0 artemisApp.programmingExercise.security.noLongerSupportedSuffix');
    });

    it('disables the version selector when no versions are known', () => {
        service.getVersions.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        // The versions are requested on construction, so re-create the component with the failing request.
        fixture.destroy();
        createComponent();
        initCreate();
        expect(comp.isVersionSelectDisabled()).toBe(true);
    });

    it('create mode stages ACTIVE/INACTIVE locally without calling the server', () => {
        initCreate();
        comp.onToggleChanged(false);
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expectNoWriteRequest();
    });

    it('create mode deactivates without a confirmation and shows a hint instead', () => {
        initCreate();
        const confirmSpy = vi.spyOn(confirmationService(), 'confirm');
        comp.onToggleChanged(false);
        expect(confirmSpy).not.toHaveBeenCalled();
        expect(comp.showStagedDeactivationHint()).toBe(true);
    });

    it('edit mode activation goes GENERATING then ACTIVE', () => {
        initEdit(inactiveConfig());
        const activate$ = new Subject<SecurityFrameworkConfig>();
        service.activate.mockReturnValue(activate$.asObservable());
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.GENERATING);
        expect(comp.isBusy()).toBe(true);
        expect(service.activate).toHaveBeenCalledWith(42, '3.4.1');
        activate$.next(activeConfig());
        activate$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
    });

    it('edit mode deactivation asks for confirmation, then goes DELETING then INACTIVE', () => {
        initEdit(activeConfig());
        const deactivate$ = new Subject<SecurityFrameworkConfig>();
        service.deactivate.mockReturnValue(deactivate$.asObservable());
        const request = captureConfirmation();

        comp.onToggleChanged(false);
        expect(service.deactivate).not.toHaveBeenCalled();
        expect(comp.isToggleOn()).toBe(false);

        request().accept();
        expect(comp.status()).toBe(SecurityActivationStatus.DELETING);
        expect(service.deactivate).toHaveBeenCalledOnce();
        expect(service.deactivate).toHaveBeenCalledWith(42);
        deactivate$.next(inactiveConfig());
        deactivate$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
    });

    it('cancelling the deactivation confirmation sends no request and turns the switch back on', () => {
        initEdit(activeConfig());
        const request = captureConfirmation();

        comp.onToggleChanged(false);
        expect(comp.isToggleOn()).toBe(false);
        request().reject!();

        expect(service.deactivate).not.toHaveBeenCalled();
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.isToggleOn()).toBe(true);
    });

    it('drops to ERROR on activation failure, then retry re-runs it to ACTIVE', () => {
        initEdit(inactiveConfig());
        service.activate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        service.activate.mockReturnValueOnce(of(activeConfig()));
        comp.onRetry();
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(service.activate).toHaveBeenCalledTimes(2);
    });

    it('disables the version selector in ERROR so the shown version cannot diverge from the retried operation', () => {
        initEdit(inactiveConfig());
        expect(comp.isVersionSelectDisabled()).toBe(false);
        service.activate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        expect(comp.isVersionSelectDisabled()).toBe(true);
    });

    it('retry re-runs the operation that actually failed (deactivation, not activation)', () => {
        initEdit(activeConfig());
        service.deactivate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        deactivateConfirmed();
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        service.deactivate.mockReturnValueOnce(of(inactiveConfig()));
        comp.onRetry();
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        expect(service.deactivate).toHaveBeenCalledTimes(2);
        expect(service.activate).not.toHaveBeenCalled();
    });

    it('re-syncs when the framework version changes on an active exercise', () => {
        initEdit(activeConfig('3.4.1'));
        const update$ = new Subject<SecurityFrameworkConfig>();
        service.updateFrameworkVersion.mockReturnValue(update$.asObservable());
        comp.onFrameworkVersionChanged('3.3.0');
        expect(comp.status()).toBe(SecurityActivationStatus.GENERATING);
        expect(service.updateFrameworkVersion).toHaveBeenCalledWith(42, '3.3.0');
        update$.next(activeConfig('3.3.0'));
        update$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.config().frameworkVersion).toBe('3.3.0');
    });

    it('only stages the version (no re-sync) when not yet active', () => {
        initEdit(inactiveConfig());
        comp.onFrameworkVersionChanged('3.3.0');
        expect(comp.config().frameworkVersion).toBe('3.3.0');
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        expect(service.updateFrameworkVersion).not.toHaveBeenCalled();
    });

    it('on ERROR keeps the toggle off after a failed activation', () => {
        initEdit(inactiveConfig());
        service.activate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        expect(comp.isToggleOn()).toBe(false);
    });

    it('on ERROR keeps the toggle on after a failed deactivation', () => {
        initEdit(activeConfig());
        service.deactivate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        deactivateConfirmed();
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        expect(comp.isToggleOn()).toBe(true);
    });

    it('surfaces a load error instead of a misleading INACTIVE, and recovers on retry', () => {
        service.getConfig.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.componentRef.setInput('mode', 'edit');
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();
        expect(comp.loadFailed()).toBe(true);

        service.getConfig.mockReturnValue(of(activeConfig()));
        comp.retryLoadConfig();
        expect(comp.loadFailed()).toBe(false);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
    });

    it('blocks the toggle and version selector while the edit-mode config is loading', () => {
        const load$ = new Subject<SecurityFrameworkConfig>();
        service.getConfig.mockReturnValue(load$.asObservable());
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.componentRef.setInput('mode', 'edit');
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();
        expect(comp.isBusy()).toBe(true);
        expect(comp.isVersionSelectDisabled()).toBe(true);

        load$.next(activeConfig());
        load$.complete();
        expect(comp.isBusy()).toBe(false);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
    });

    it('ignores a late config load while an operation is in flight so it cannot overwrite the result', () => {
        const load$ = new Subject<SecurityFrameworkConfig>();
        service.getConfig.mockReturnValue(load$.asObservable());
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.componentRef.setInput('mode', 'edit');
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();

        const activate$ = new Subject<SecurityFrameworkConfig>();
        service.activate.mockReturnValue(activate$.asObservable());
        comp.onToggleChanged(true);

        load$.next(inactiveConfig());
        load$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.GENERATING);

        activate$.next(activeConfig());
        activate$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
    });

    it('never sends a write request outside edit mode, even for an exercise with an id', () => {
        // Create mode is the default; an exercise id alone must not switch the card to edit mode.
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();

        comp.onToggleChanged(false);
        comp.onToggleChanged(true);
        comp.onFrameworkVersionChanged('3.3.0');
        comp.onRepair();
        comp.onDeactivate();
        comp.onRetry();

        expect(service.getConfig).not.toHaveBeenCalled();
        expectNoWriteRequest();
    });

    it('emits the choice to the parent only where it differs from the template', () => {
        initCreate();
        comp.onToggleChanged(false);
        fixture.detectChanges();
        expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.DEACTIVATE });

        comp.onToggleChanged(true);
        fixture.detectChanges();
        expect(emitted.at(-1)).toBeUndefined();

        comp.onFrameworkVersionChanged('3.3.0');
        fixture.detectChanges();
        expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.ACTIVATE, frameworkVersion: '3.3.0' });

        comp.onToggleChanged(false);
        fixture.detectChanges();
        expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.DEACTIVATE, frameworkVersion: '3.3.0' });
    });

    it('re-seeds a staged choice provided by the parent when the card is re-created', () => {
        fixture.componentRef.setInput('stagedChoice', { action: SecurityFrameworkAction.DEACTIVATE, frameworkVersion: '3.3.0' });
        initCreate();
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        expect(comp.config().frameworkVersion).toBe('3.3.0');
        expect(emitted).toEqual([]);
    });

    it('hands its choice back to the parent when the parent cleared it on a non-Java switch', () => {
        initCreate();
        comp.onToggleChanged(false);
        fixture.detectChanges();
        expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.DEACTIVATE });

        // The parent clears the choice when the language changes away from Java; the hidden card does not answer.
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.C);
        fixture.componentRef.setInput('stagedChoice', undefined);
        fixture.detectChanges();
        expect(emitted).toHaveLength(1);

        // Back on Java, the card still shows the deactivation, so the parent holds it again.
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.DEACTIVATE });
    });

    describe('import from an exercise', () => {
        it('loads the source config read-only and sends no write request, including version changes and retry', () => {
            initImportFromExercise(activeConfig('3.4.1'));
            expect(service.getConfig).toHaveBeenCalledOnce();
            expect(service.getConfig).toHaveBeenCalledWith(7);
            expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);

            const confirmSpy = vi.spyOn(confirmationService(), 'confirm');
            comp.onToggleChanged(false);
            fixture.detectChanges();
            expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.DEACTIVATE });
            expect(confirmSpy).not.toHaveBeenCalled();

            comp.onToggleChanged(true);
            comp.onFrameworkVersionChanged('3.3.0');
            fixture.detectChanges();
            expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.ACTIVATE, frameworkVersion: '3.3.0' });

            comp.onRetry();
            comp.onRepair();
            comp.onDeactivate();
            expectNoWriteRequest();
        });

        it('sends nothing while the choice matches the source', () => {
            initImportFromExercise(inactiveConfig());
            comp.onToggleChanged(true);
            comp.onToggleChanged(false);
            fixture.detectChanges();
            expect(comp.choiceToSend()).toBeUndefined();
            expect(emitted).toEqual([]);
        });

        it('activates an inactive source with the selected version', () => {
            initImportFromExercise({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '2.0.0' });
            comp.onToggleChanged(true);
            fixture.detectChanges();
            // The source pins a version that is no longer selectable, so activating switches to the template's version.
            expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.ACTIVATE, frameworkVersion: '3.2.2' });
            expectNoWriteRequest();
        });

        it('discards a late config response of a previous source', () => {
            const firstLoad$ = new Subject<SecurityFrameworkConfig>();
            service.getConfig.mockReturnValueOnce(firstLoad$.asObservable());
            fixture.componentRef.setInput('programmingExercise', javaExercise(7));
            fixture.componentRef.setInput('mode', 'import');
            fixture.componentRef.setInput('sourceExerciseId', 7);
            fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
            fixture.detectChanges();

            service.getConfig.mockReturnValueOnce(of(inactiveConfig()));
            fixture.componentRef.setInput('programmingExercise', javaExercise(8));
            fixture.componentRef.setInput('sourceExerciseId', 8);
            fixture.detectChanges();
            expect(service.getConfig).toHaveBeenLastCalledWith(8);
            expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);

            firstLoad$.next(activeConfig());
            firstLoad$.complete();
            expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
            expect(comp.sourceConfig()?.status).toBe(SecurityActivationStatus.INACTIVE);
        });

        it('shows the reasons of an UNSUPPORTED source without a switch or version select, and sends nothing', () => {
            initImportFromExercise(unsupportedConfig());
            expect(comp.showToggle()).toBe(false);
            expect(comp.showVersionSelect()).toBe(false);
            expect(comp.problemMessages()).toEqual([
                { key: 'artemisApp.programmingExercise.security.unsupportedReason.ARES_1', params: { location: 'build.gradle' } },
                { key: 'artemisApp.programmingExercise.security.unsupportedReason.NO_ARES_VERSION_PINNED', params: { location: 'build.gradle' } },
            ]);
            expect(comp.choiceToSend()).toBeUndefined();
        });

        it('offers keep, repair or deactivate for a MISCONFIGURED source', () => {
            initImportFromExercise(misconfiguredConfig());
            expect(comp.usesImportActionChoice()).toBe(true);
            comp.onImportActionChanged(SecurityFrameworkAction.ACTIVATE);
            fixture.detectChanges();
            expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.ACTIVATE });
            expectNoWriteRequest();
        });
    });

    describe('import from a file or the sharing platform', () => {
        it('offers a three-way choice that defaults to keeping the imported setting', () => {
            initImportWithUnknownState();
            expect(comp.usesImportActionChoice()).toBe(true);
            expect(comp.isStateUnknown()).toBe(true);
            expect(comp.importAction()).toBe('KEEP');
            expect(comp.choiceToSend()).toBeUndefined();
            expect(service.getConfig).not.toHaveBeenCalled();
            expect(emitted).toEqual([]);
        });

        it('emits the chosen action and nothing for keep', () => {
            initImportWithUnknownState();
            comp.onImportActionChanged(SecurityFrameworkAction.DEACTIVATE);
            fixture.detectChanges();
            expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.DEACTIVATE });
            expect(comp.showStagedDeactivationHint()).toBe(true);

            comp.onImportActionChanged(SecurityFrameworkAction.ACTIVATE);
            fixture.detectChanges();
            expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.ACTIVATE });

            comp.onImportActionChanged('KEEP');
            fixture.detectChanges();
            expect(emitted.at(-1)).toBeUndefined();
            expectNoWriteRequest();
        });
    });

    describe('MISCONFIGURED in edit mode', () => {
        it('repairs by activating with the current version', () => {
            initEdit(misconfiguredConfig());
            service.activate.mockReturnValue(of(activeConfig('3.3.0')));
            expect(comp.showToggle()).toBe(false);
            expect(comp.isBusy()).toBe(false);

            comp.onRepair();

            expect(service.activate).toHaveBeenCalledExactlyOnceWith(42, '3.3.0');
            expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        });

        it('deactivates only after confirmation', () => {
            initEdit(misconfiguredConfig());
            service.deactivate.mockReturnValue(of(inactiveConfig()));
            const request = captureConfirmation();

            comp.onDeactivate();
            expect(service.deactivate).not.toHaveBeenCalled();
            request().accept();

            expect(service.deactivate).toHaveBeenCalledExactlyOnceWith(42);
        });

        it('explains which policy file breaks which tests and whether it can be repaired', () => {
            initEdit(misconfiguredConfig(SecurityPolicyFileProblem.UNPARSEABLE));
            expect(comp.problemMessages()).toEqual([
                { key: 'artemisApp.programmingExercise.security.misconfigured.UNPARSEABLE', params: { path: 'SecurityPolicy.yaml', classes: 'de.x.ATest, de.x.BTest' } },
            ]);
            expect(comp.hasUnrepairableProblem()).toBe(true);
        });

        it('treats a missing policy file as repairable', () => {
            initEdit(misconfiguredConfig(SecurityPolicyFileProblem.MISSING));
            expect(comp.hasUnrepairableProblem()).toBe(false);
        });
    });

    it('lists the test classes missing in an active policy and the policy files that could not be checked', () => {
        initEdit({
            status: SecurityActivationStatus.ACTIVE,
            frameworkVersion: '3.4.1',
            disabledTestClasses: ['de.x.OffTest'],
            policyFiles: [
                { policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A'], missingTestClasses: ['de.x.New'] },
                { policyPath: 'Other.yaml', referencedBy: ['de.x.B'], problem: SecurityPolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND },
            ],
        });
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.missingTestClasses()).toEqual(['de.x.New']);
        expect(comp.uncheckedPolicyFiles()).toEqual(['Other.yaml']);
        expect(comp.problemMessages()).toEqual([]);
    });
});

/** Rendering spec: the real template, for the states whose text is derived from the config rather than only the status. */
describe('ProgrammingExerciseSecurityComponent rendering', () => {
    let fixture: ComponentFixture<ProgrammingExerciseSecurityComponent>;
    let service: SecurityServiceMock;

    beforeEach(() => {
        service = createServiceMock();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), { provide: TranslateService, useClass: MockTranslateService }, { provide: SecurityFrameworkService, useValue: service }],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseSecurityComponent);
    });

    function renderEdit(config: SecurityFrameworkConfig): HTMLElement {
        service.getConfig.mockReturnValue(of(config));
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.componentRef.setInput('mode', 'edit');
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    const query = (element: HTMLElement, testId: string) => element.querySelector(`[data-testid="${testId}"]`);

    it('renders MISCONFIGURED with the broken policy file, Repair and Deactivate, and no switch', () => {
        const element = renderEdit(misconfiguredConfig(SecurityPolicyFileProblem.TOO_LARGE));
        expect(query(element, 'security-card')?.classList).toContain('sf-card--misconfigured');
        expect(query(element, 'security-misconfigured-problems')?.textContent).toContain('artemisApp.programmingExercise.security.misconfigured.TOO_LARGE');
        expect(query(element, 'security-not-repairable')).not.toBeNull();
        expect(query(element, 'security-repair')).not.toBeNull();
        expect(query(element, 'security-deactivate')).not.toBeNull();
        expect(query(element, 'security-activate-toggle')).toBeNull();
        expect(query(element, 'security-framework-version-select')).not.toBeNull();
    });

    it('renders UNSUPPORTED with its reasons and neither a switch nor a version select', () => {
        const element = renderEdit(unsupportedConfig());
        expect(query(element, 'security-card')?.classList).toContain('sf-card--unsupported');
        const reasons = query(element, 'security-unsupported-reasons')?.querySelectorAll('li');
        expect(reasons).toHaveLength(2);
        expect(reasons?.[0].textContent).toContain('artemisApp.programmingExercise.security.unsupportedReason.ARES_1');
        expect(query(element, 'security-activate-toggle')).toBeNull();
        expect(query(element, 'security-framework-version-select')).toBeNull();
        expect(query(element, 'security-repair')).toBeNull();
    });

    it('renders the three-way choice for an import from a file', () => {
        fixture.componentRef.setInput('programmingExercise', javaExercise());
        fixture.componentRef.setInput('mode', 'import');
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();
        const element = fixture.nativeElement as HTMLElement;
        expect(query(element, 'security-import-action')?.querySelectorAll('button')).toHaveLength(3);
        expect(query(element, 'security-activate-toggle')).toBeNull();
        expect(query(element, 'security-framework-version-select')).toBeNull();
    });
});
