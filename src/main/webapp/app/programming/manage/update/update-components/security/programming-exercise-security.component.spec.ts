import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, provideHttpClient } from '@angular/common/http';
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
    SecurityVersionCompatibilityVerdict,
} from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
import { ProgrammingExerciseSecurityComponent } from 'app/programming/manage/update/update-components/security/programming-exercise-security.component';

const VERSIONS: SecurityFrameworkVersions = {
    selectableVersions: ['3.4.1', '3.3.0', '3.2.2'],
    templateVersions: { [ProjectType.PLAIN_GRADLE]: '3.2.2', [ProjectType.PLAIN_MAVEN]: '3.3.0', [ProjectType.MAVEN_MAVEN]: '3.4.1' },
};

const inactiveConfig = (): SecurityFrameworkConfig => ({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '3.4.1' });
const configuredConfig = (version = '3.4.1'): SecurityFrameworkConfig => ({ status: SecurityActivationStatus.CONFIGURED, frameworkVersion: version });
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
    addDiscoveredTestClasses: ReturnType<typeof vi.fn>;
};

const createServiceMock = (): SecurityServiceMock => ({
    getVersions: vi.fn().mockReturnValue(of(VERSIONS)),
    getConfig: vi.fn().mockReturnValue(of(inactiveConfig())),
    activate: vi.fn(),
    deactivate: vi.fn(),
    updateFrameworkVersion: vi.fn(),
    addDiscoveredTestClasses: vi.fn(),
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
        initEdit(configuredConfig('3.3.0'));
        expect(service.getConfig).toHaveBeenCalledWith(42);
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
        expect(comp.config().frameworkVersion).toBe('3.3.0');
    });

    it('create mode starts CONFIGURED with the template version, like the Java template', () => {
        initCreate();
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
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
        service.activate.mockReturnValue(of(configuredConfig('2.0.0')));

        comp.onToggleChanged(true);

        expect(service.activate).toHaveBeenCalledWith(42, '2.0.0');
    });

    it('ignores choosing a version that is not selectable', () => {
        initEdit(configuredConfig('2.0.0'));
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
        service.activate.mockReturnValue(of(configuredConfig('3.2.2')));
        comp.onToggleChanged(true);
        expect(service.activate).toHaveBeenCalledWith(42, '3.2.2');
    });

    it('activates with the newest selectable version when the exercise and the template both pin unsupported versions', () => {
        service.getVersions.mockReturnValue(of({ selectableVersions: ['3.4.1', '3.3.0'], templateVersions: { [ProjectType.PLAIN_GRADLE]: '2.0.0' } }));
        fixture.destroy();
        createComponent();
        initEdit({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '1.9.0' });
        service.activate.mockReturnValue(of(configuredConfig('3.4.1')));

        comp.onToggleChanged(true);

        expect(service.activate).toHaveBeenCalledWith(42, '3.4.1');
        // The hint about the template version belongs to creating an exercise.
        expect(comp.unsupportedTemplateVersion()).toBeUndefined();
    });

    it('labels the newest version and marks a no longer supported version the exercise pins', () => {
        initEdit(configuredConfig('2.0.0'));
        expect(comp.frameworkVersions().map((option) => option.version)).toEqual(['3.4.1', '3.3.0', '3.2.2', '2.0.0']);
        expect(comp.frameworkVersions()[0].label).toBe('3.4.1 artemisApp.programmingExercise.security.latestSuffix');
        expect(comp.frameworkVersions()[1].label).toBe('3.3.0');
        expect(comp.frameworkVersions()[3].label).toBe('2.0.0 artemisApp.programmingExercise.security.noLongerSupportedSuffix');
    });

    describe('versions that do not accept the policy (B10)', () => {
        const INCOMPATIBILITY = 'SecurityPolicy.yaml: regardingTheSupervisedCode.theFollowingTestBehaviorIsConfigured.regardingStrictTimeouts';
        const withCompatibilities = (): SecurityFrameworkConfig => ({
            ...configuredConfig('3.4.1'),
            versionCompatibilities: [
                { version: '3.4.1', verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE },
                { version: '3.3.0', verdict: SecurityVersionCompatibilityVerdict.INCOMPATIBLE, incompatibilities: [INCOMPATIBILITY] },
                { version: '3.2.2', verdict: SecurityVersionCompatibilityVerdict.UNVERIFIED },
            ],
        });

        it('marks incompatible and unverified versions in the dropdown', () => {
            initEdit(withCompatibilities());
            expect(comp.frameworkVersions().map((option) => option.label)).toEqual([
                '3.4.1 artemisApp.programmingExercise.security.latestSuffix',
                '3.3.0 artemisApp.programmingExercise.security.incompatibleSuffix',
                '3.2.2 artemisApp.programmingExercise.security.unverifiedSuffix',
            ]);
            expect(comp.incompatibleVersions().map((compatibility) => compatibility.version)).toEqual(['3.3.0']);
        });

        it('does not request a version that does not accept the policy, but still allows the others', () => {
            initEdit(withCompatibilities());
            service.updateFrameworkVersion.mockReturnValue(of(configuredConfig('3.2.2')));

            comp.onFrameworkVersionChanged('3.3.0');
            expect(service.updateFrameworkVersion).not.toHaveBeenCalled();
            expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);

            comp.onFrameworkVersionChanged('3.2.2');
            expect(service.updateFrameworkVersion).toHaveBeenCalledWith(42, '3.2.2');
        });

        it('marks nothing when the server checked no policy file', () => {
            initEdit(configuredConfig('3.4.1'));
            expect(comp.frameworkVersions()[1].label).toBe('3.3.0');
            expect(comp.incompatibleVersions()).toEqual([]);
        });

        it('passes the parameters of a refused version to the translation of the error', () => {
            initEdit(configuredConfig('3.4.1'));
            const instant = vi.spyOn(TestBed.inject(TranslateService), 'instant').mockReturnValue('Ares 3.3.0 does not accept it');
            const params = { version: '3.3.0', details: INCOMPATIBILITY };
            service.updateFrameworkVersion.mockReturnValue(
                throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'error.securityFrameworkVersionIncompatible', params } })),
            );

            comp.onFrameworkVersionChanged('3.3.0');

            expect(instant).toHaveBeenCalledWith('error.securityFrameworkVersionIncompatible', params);
            expect(comp.config().errorDetail).toBe('Ares 3.3.0 does not accept it');
        });
    });

    it('disables the version selector when no versions are known', () => {
        service.getVersions.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        // The versions are requested on construction, so re-create the component with the failing request.
        fixture.destroy();
        createComponent();
        initCreate();
        expect(comp.isVersionSelectDisabled()).toBe(true);
    });

    it('create mode stages CONFIGURED/INACTIVE locally without calling the server', () => {
        initCreate();
        comp.onToggleChanged(false);
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
        expectNoWriteRequest();
    });

    it('create mode deactivates without a confirmation and shows a hint instead', () => {
        initCreate();
        const confirmSpy = vi.spyOn(confirmationService(), 'confirm');
        comp.onToggleChanged(false);
        expect(confirmSpy).not.toHaveBeenCalled();
        expect(comp.showStagedDeactivationHint()).toBe(true);
    });

    it('edit mode activation goes GENERATING then CONFIGURED', () => {
        initEdit(inactiveConfig());
        const activate$ = new Subject<SecurityFrameworkConfig>();
        service.activate.mockReturnValue(activate$.asObservable());
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.GENERATING);
        expect(comp.isBusy()).toBe(true);
        expect(service.activate).toHaveBeenCalledWith(42, '3.4.1');
        activate$.next(configuredConfig());
        activate$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
    });

    it('edit mode deactivation asks for confirmation, then goes DELETING then INACTIVE', () => {
        initEdit(configuredConfig());
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

    it('names the other parts of Ares the tests use in the confirmation, because the Ares library then stays in the build (B4)', () => {
        initEdit({
            ...configuredConfig(),
            aresApiUses: [
                { location: 'test/test/ClassTest.java', api: 'structural.ClassTestProvider' },
                { location: 'test/test/ClassTest.java', api: 'StrictTimeout' },
                { location: 'test/test/MethodTest.java', api: 'StrictTimeout' },
            ],
        });
        const request = captureConfirmation();
        const instant = vi.spyOn(TestBed.inject(TranslateService), 'instant');

        comp.onToggleChanged(false);

        expect(request().message).toBe(
            'artemisApp.programmingExercise.security.deactivateConfirmation.message artemisApp.programmingExercise.security.deactivateConfirmation.aresLibraryStays',
        );
        expect(instant).toHaveBeenCalledWith('artemisApp.programmingExercise.security.deactivateConfirmation.aresLibraryStays', {
            apis: 'structural.ClassTestProvider, StrictTimeout',
        });
    });

    it('leaves the library sentence out of the confirmation when the tests use only the sandbox', () => {
        initEdit(configuredConfig());
        const request = captureConfirmation();

        comp.onToggleChanged(false);

        expect(request().message).toBe('artemisApp.programmingExercise.security.deactivateConfirmation.message');
    });

    it('cancelling the deactivation confirmation sends no request and turns the switch back on', () => {
        initEdit(configuredConfig());
        const request = captureConfirmation();

        comp.onToggleChanged(false);
        expect(comp.isToggleOn()).toBe(false);
        request().reject!();

        expect(service.deactivate).not.toHaveBeenCalled();
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
        expect(comp.isToggleOn()).toBe(true);
    });

    it('drops to ERROR on activation failure, then retry re-runs it to CONFIGURED', () => {
        initEdit(inactiveConfig());
        service.activate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        service.activate.mockReturnValueOnce(of(configuredConfig()));
        comp.onRetry();
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
        expect(service.activate).toHaveBeenCalledTimes(2);
    });

    it('disables the version selector in ERROR so the shown version cannot diverge from the retried operation', () => {
        initEdit(configuredConfig());
        expect(comp.isVersionSelectDisabled()).toBe(false);
        service.updateFrameworkVersion.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onFrameworkVersionChanged('3.3.0');
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        expect(comp.isVersionSelectDisabled()).toBe(true);
    });

    it('retry re-runs the operation that actually failed (deactivation, not activation)', () => {
        initEdit(configuredConfig());
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
        initEdit(configuredConfig('3.4.1'));
        const update$ = new Subject<SecurityFrameworkConfig>();
        service.updateFrameworkVersion.mockReturnValue(update$.asObservable());
        comp.onFrameworkVersionChanged('3.3.0');
        expect(comp.status()).toBe(SecurityActivationStatus.GENERATING);
        expect(service.updateFrameworkVersion).toHaveBeenCalledWith(42, '3.3.0');
        update$.next(configuredConfig('3.3.0'));
        update$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
        expect(comp.config().frameworkVersion).toBe('3.3.0');
    });

    it('locks the version selector of an inactive exercise, whose version can only change while active, and shows the version the repository pins', () => {
        initEdit(inactiveConfig());
        expect(comp.isVersionSelectDisabled()).toBe(true);

        comp.onFrameworkVersionChanged('3.3.0');

        expect(comp.config().frameworkVersion).toBe('3.4.1');
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        expect(service.updateFrameworkVersion).not.toHaveBeenCalled();
    });

    it('keeps the version selector of a new exercise enabled while deactivated, because creating writes the picked version', () => {
        initCreate();
        comp.onToggleChanged(false);
        expect(comp.isVersionSelectDisabled()).toBe(false);

        comp.onFrameworkVersionChanged('3.3.0');
        fixture.detectChanges();

        expect(comp.choiceToSend()).toEqual({ action: SecurityFrameworkAction.DEACTIVATE, frameworkVersion: '3.3.0' });
    });

    it('on ERROR keeps the toggle off after a failed activation', () => {
        initEdit(inactiveConfig());
        service.activate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        expect(comp.isToggleOn()).toBe(false);
    });

    it('on ERROR keeps the toggle on after a failed deactivation', () => {
        initEdit(configuredConfig());
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

        service.getConfig.mockReturnValue(of(configuredConfig()));
        comp.retryLoadConfig();
        expect(comp.loadFailed()).toBe(false);
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
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

        load$.next(configuredConfig());
        load$.complete();
        expect(comp.isBusy()).toBe(false);
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
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

        activate$.next(configuredConfig());
        activate$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
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
            initImportFromExercise(configuredConfig('3.4.1'));
            expect(service.getConfig).toHaveBeenCalledOnce();
            expect(service.getConfig).toHaveBeenCalledWith(7);
            expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);

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

        it('locks the version selector while the import is deactivated and shows the version of the source it keeps', () => {
            initImportFromExercise(configuredConfig('3.4.1'));
            comp.onFrameworkVersionChanged('3.3.0');
            expect(comp.config().frameworkVersion).toBe('3.3.0');

            comp.onToggleChanged(false);
            comp.onFrameworkVersionChanged('3.2.2');
            fixture.detectChanges();

            expect(comp.isVersionSelectDisabled()).toBe(true);
            expect(comp.config().frameworkVersion).toBe('3.4.1');
            expect(emitted.at(-1)).toEqual({ action: SecurityFrameworkAction.DEACTIVATE });
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

            firstLoad$.next(configuredConfig());
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
            service.activate.mockReturnValue(of(configuredConfig('3.3.0')));
            expect(comp.showToggle()).toBe(false);
            expect(comp.isBusy()).toBe(false);

            comp.onRepair();

            expect(service.activate).toHaveBeenCalledExactlyOnceWith(42, '3.3.0');
            expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
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

        it('repairs with a newly chosen version, because that version may accept a policy the pinned one refuses', () => {
            initEdit(misconfiguredConfig(SecurityPolicyFileProblem.UNPARSEABLE));
            const activate$ = new Subject<SecurityFrameworkConfig>();
            service.activate.mockReturnValue(activate$.asObservable());

            comp.onFrameworkVersionChanged('3.4.1');

            expect(service.activate).toHaveBeenCalledExactlyOnceWith(42, '3.4.1');
            expect(comp.status()).toBe(SecurityActivationStatus.GENERATING);
            activate$.next(configuredConfig('3.4.1'));
            activate$.complete();
            expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
            expect(comp.config().frameworkVersion).toBe('3.4.1');
        });

        it('keeps the pinned version when the chosen one cannot repair the policy either', () => {
            initEdit(misconfiguredConfig(SecurityPolicyFileProblem.UNPARSEABLE));
            service.activate.mockReturnValue(throwError(() => new Error('STILL_INVALID')));

            comp.onFrameworkVersionChanged('3.4.1');

            expect(comp.status()).toBe(SecurityActivationStatus.MISCONFIGURED);
            expect(comp.config().frameworkVersion).toBe('3.3.0');
            expect(comp.config().errorDetail).toBe('STILL_INVALID');
        });

        it('stays MISCONFIGURED after a failed repair, so Deactivate remains available', () => {
            initEdit(misconfiguredConfig(SecurityPolicyFileProblem.MISSING));
            service.activate.mockReturnValue(throwError(() => new Error('REPAIR_FAILED')));

            comp.onRepair();

            expect(comp.status()).toBe(SecurityActivationStatus.MISCONFIGURED);
            expect(comp.config().errorDetail).toBe('REPAIR_FAILED');
            expect(comp.isBusy()).toBe(false);
        });

        it('stays MISCONFIGURED after a failed deactivation', () => {
            initEdit(misconfiguredConfig(SecurityPolicyFileProblem.UNPARSEABLE));
            service.deactivate.mockReturnValue(throwError(() => new Error('DEACTIVATE_FAILED')));
            const request = captureConfirmation();

            comp.onDeactivate();
            request().accept();

            expect(comp.status()).toBe(SecurityActivationStatus.MISCONFIGURED);
            expect(comp.config().errorDetail).toBe('DEACTIVATE_FAILED');
        });

        it('clears the error of a failed repair when the next attempt succeeds', () => {
            initEdit(misconfiguredConfig(SecurityPolicyFileProblem.MISSING));
            service.activate.mockReturnValueOnce(throwError(() => new Error('REPAIR_FAILED')));
            comp.onRepair();
            service.activate.mockReturnValueOnce(of(configuredConfig('3.3.0')));

            comp.onRepair();

            expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
            expect(comp.config().errorDetail).toBeUndefined();
        });

        describe('error detail of a refused request', () => {
            const refusal = (message: unknown) =>
                new HttpErrorResponse({ status: 400, error: { message }, headers: new HttpHeaders({ 'X-artemisApp-message': 'English text of the server' }) });

            it('shows the translation the server names', () => {
                initEdit(misconfiguredConfig(SecurityPolicyFileProblem.MISSING));
                vi.spyOn(TestBed.inject(TranslateService), 'instant').mockReturnValue('Übersetzter Text');
                service.activate.mockReturnValue(throwError(() => refusal('error.securityFrameworkPolicyNotRepairable')));

                comp.onRepair();

                expect(comp.config().errorDetail).toBe('Übersetzter Text');
            });

            it('falls back to the English text if the translation is missing', () => {
                initEdit(misconfiguredConfig(SecurityPolicyFileProblem.MISSING));
                vi.spyOn(TestBed.inject(TranslateService), 'instant').mockReturnValue('translation-not-found[error.unknown]');
                service.activate.mockReturnValue(throwError(() => refusal('error.unknown')));

                comp.onRepair();

                expect(comp.config().errorDetail).toBe('English text of the server');
            });

            it('falls back to the English text if the server names no translation', () => {
                initEdit(misconfiguredConfig(SecurityPolicyFileProblem.MISSING));
                service.activate.mockReturnValue(throwError(() => refusal(undefined)));

                comp.onRepair();

                expect(comp.config().errorDetail).toBe('English text of the server');
            });
        });
    });

    it('lists the test classes missing in an active policy and the policy files that could not be checked', () => {
        initEdit({
            status: SecurityActivationStatus.CONFIGURED,
            frameworkVersion: '3.4.1',
            disabledTestClasses: ['de.x.OffTest'],
            policyFiles: [
                { policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A'], missingTestClasses: ['de.x.New'] },
                { policyPath: 'Other.yaml', referencedBy: ['de.x.B'], problem: SecurityPolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND },
            ],
        });
        expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
        expect(comp.missingTestClasses()).toEqual(['de.x.New']);
        expect(comp.uncheckedPolicyFiles()).toEqual(['Other.yaml']);
        expect(comp.problemMessages()).toEqual([]);
    });

    describe('adding the discovered test classes (B5)', () => {
        const withMissingClass = (): SecurityFrameworkConfig => ({
            ...configuredConfig(),
            policyFiles: [{ policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A', 'de.x.New'], missingTestClasses: ['de.x.New'] }],
        });

        it('commits the missing test classes through the server and settles on the returned config', () => {
            initEdit(withMissingClass());
            const add$ = new Subject<SecurityFrameworkConfig>();
            service.addDiscoveredTestClasses.mockReturnValue(add$.asObservable());

            comp.onAddDiscoveredTestClasses();

            expect(service.addDiscoveredTestClasses).toHaveBeenCalledWith(42);
            expect(comp.status()).toBe(SecurityActivationStatus.GENERATING);
            add$.next({ ...configuredConfig(), policyFiles: [{ policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A', 'de.x.New'] }] });
            add$.complete();
            expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
            expect(comp.missingTestClasses()).toEqual([]);
        });

        it('drops to ERROR if the server refuses, and retry runs the same operation again', () => {
            initEdit(withMissingClass());
            service.addDiscoveredTestClasses.mockReturnValueOnce(throwError(() => new Error('PUSH_FAILED')));

            comp.onAddDiscoveredTestClasses();
            expect(comp.status()).toBe(SecurityActivationStatus.ERROR);

            service.addDiscoveredTestClasses.mockReturnValueOnce(of(configuredConfig()));
            comp.onRetry();
            expect(service.addDiscoveredTestClasses).toHaveBeenCalledTimes(2);
            expect(comp.status()).toBe(SecurityActivationStatus.CONFIGURED);
        });

        it('never sends the request outside edit mode', () => {
            initCreate();
            comp.onAddDiscoveredTestClasses();
            expect(service.addDiscoveredTestClasses).not.toHaveBeenCalled();
        });
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

    it('renders a configured sandbox as CONFIGURED, never as confirmed ready, because no Ares build result confirms it yet (B11)', () => {
        const element = renderEdit(configuredConfig('3.3.0'));
        expect(query(element, 'security-card')?.classList).toContain('sf-card--configured');
        expect(query(element, 'security-status-pill')?.classList).toContain('sf-pill--configured');
        expect(element.querySelector('[class*="--active"]')).toBeNull();
    });

    it('lists what each version that does not accept the policy does not accept, and nothing without a checked policy (B10)', () => {
        const element = renderEdit({
            ...configuredConfig('3.4.1'),
            versionCompatibilities: [
                { version: '3.4.1', verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE },
                { version: '3.3.0', verdict: SecurityVersionCompatibilityVerdict.INCOMPATIBLE, incompatibilities: ['SecurityPolicy.yaml: a.b'] },
            ],
        });
        const items = query(element, 'security-incompatible-versions')?.querySelectorAll('li');
        expect(items).toHaveLength(1);
        expect(items?.[0].textContent).toContain('artemisApp.programmingExercise.security.versionIncompatible');
    });

    it('offers to add the missing test classes in edit mode only while some are missing (B5)', () => {
        const element = renderEdit({
            ...configuredConfig('3.4.1'),
            policyFiles: [{ policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A', 'de.x.New'], missingTestClasses: ['de.x.New'] }],
        });
        expect(query(element, 'security-add-test-classes')).not.toBeNull();
    });

    it('does not offer to add test classes when every test class is listed', () => {
        const element = renderEdit(configuredConfig('3.4.1'));
        expect(query(element, 'security-add-test-classes')).toBeNull();
    });

    it('does not let the real select choose a version that does not accept the policy, so the shown version stays the saved one (B10)', async () => {
        const element = renderEdit({
            ...configuredConfig('3.4.1'),
            versionCompatibilities: [
                { version: '3.4.1', verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE },
                { version: '3.3.0', verdict: SecurityVersionCompatibilityVerdict.INCOMPATIBLE, incompatibilities: ['SecurityPolicy.yaml: a.b'] },
                { version: '3.2.2', verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE },
            ],
        });
        // ngModel writes its value to the select asynchronously.
        await fixture.whenStable();
        fixture.detectChanges();
        const trigger = query(element, 'security-framework-version-select')?.querySelector('button[aria-haspopup="listbox"]') as HTMLButtonElement;
        const shownVersion = () => trigger.textContent?.trim();
        expect(shownVersion()).toBe('3.4.1 artemisApp.programmingExercise.security.latestSuffix');

        trigger.click();
        fixture.detectChanges();
        const options = Array.from(document.querySelectorAll('[role="option"]')) as HTMLElement[];
        const incompatible = options.find((option) => option.textContent?.includes('3.3.0'))!;
        expect(incompatible.getAttribute('aria-disabled')).toBe('true');
        incompatible.click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(service.updateFrameworkVersion).not.toHaveBeenCalled();
        expect(shownVersion()).toBe('3.4.1 artemisApp.programmingExercise.security.latestSuffix');
        expect(fixture.componentInstance.config().frameworkVersion).toBe('3.4.1');
    });

    it('shows no list of incompatible versions when the server checked no policy file', () => {
        const element = renderEdit(configuredConfig('3.4.1'));
        expect(query(element, 'security-incompatible-versions')).toBeNull();
    });

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

    it('disables Repair for a policy file it cannot repair and keeps Deactivate enabled', () => {
        const element = renderEdit(misconfiguredConfig(SecurityPolicyFileProblem.UNPARSEABLE));
        expect((query(element, 'security-repair') as HTMLButtonElement).disabled).toBe(true);
        expect((query(element, 'security-deactivate') as HTMLButtonElement).disabled).toBe(false);
    });

    it('enables Repair for a missing policy file', () => {
        const element = renderEdit(misconfiguredConfig(SecurityPolicyFileProblem.MISSING));
        expect((query(element, 'security-repair') as HTMLButtonElement).disabled).toBe(false);
    });

    it('sends the version chosen in the real select of a misconfigured exercise to the server, even though Repair is disabled', async () => {
        const element = renderEdit(misconfiguredConfig(SecurityPolicyFileProblem.UNPARSEABLE));
        service.activate.mockReturnValue(of(configuredConfig('3.4.1')));
        await fixture.whenStable();
        fixture.detectChanges();
        expect((query(element, 'security-repair') as HTMLButtonElement).disabled).toBe(true);

        (query(element, 'security-framework-version-select')?.querySelector('button[aria-haspopup="listbox"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        const option = (Array.from(document.querySelectorAll('[role="option"]')) as HTMLElement[]).find((candidate) => candidate.textContent?.includes('3.4.1'))!;
        option.click();
        fixture.detectChanges();

        expect(service.activate).toHaveBeenCalledExactlyOnceWith(42, '3.4.1');
        expect(query(element, 'security-card')?.classList).toContain('sf-card--configured');
    });

    it('shows why a repair failed and keeps Repair and Deactivate', () => {
        const element = renderEdit(misconfiguredConfig(SecurityPolicyFileProblem.MISSING));
        service.activate.mockReturnValue(throwError(() => new Error('REPAIR_FAILED')));

        (query(element, 'security-repair') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(query(element, 'security-error-detail')?.textContent).toContain('REPAIR_FAILED');
        expect(query(element, 'security-repair')).not.toBeNull();
        expect(query(element, 'security-deactivate')).not.toBeNull();
        expect(query(element, 'security-retry')).toBeNull();
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
