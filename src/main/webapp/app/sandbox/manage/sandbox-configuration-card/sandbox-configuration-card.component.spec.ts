import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiConfirmationRequest, TumAetUiConfirmationService } from '@tumaet/ui-angular/confirm-dialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SandboxApi } from 'app/openapi/api/sandbox-api';
import { SandboxConfiguration } from 'app/openapi/model/sandbox-configuration';
import { SandboxFrameworkVersions } from 'app/openapi/model/sandbox-framework-versions';
import { SandboxPolicyFileProblemEnum } from 'app/openapi/model/sandbox-policy-file';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { SandboxCardStatus } from 'app/sandbox/shared/entities/sandbox-card-state.model';
import { SandboxConfigurationCardComponent } from 'app/sandbox/manage/sandbox-configuration-card/sandbox-configuration-card.component';

const VERSIONS: SandboxFrameworkVersions = {
    selectableVersions: ['3.4.1', '3.3.0', '3.2.2'],
    templateVersions: { [ProjectType.PLAIN_GRADLE]: '3.2.2', [ProjectType.PLAIN_MAVEN]: '3.3.0', [ProjectType.MAVEN_MAVEN]: '3.4.1' },
};

const inactiveConfig = (): SandboxConfiguration => ({ status: 'INACTIVE', frameworkVersion: '3.4.1' });
const configuredConfig = (version = '3.4.1'): SandboxConfiguration => ({ status: 'CONFIGURED', frameworkVersion: version });
const misconfiguredConfig = (problem: SandboxPolicyFileProblemEnum = 'MISSING'): SandboxConfiguration => ({
    status: 'MISCONFIGURED',
    frameworkVersion: '3.3.0',
    policyFiles: [{ policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.ATest', 'de.x.BTest'], problem }],
});
const unsupportedConfig = (): SandboxConfiguration => ({
    status: 'UNSUPPORTED',
    unsupportedReasons: [
        { location: 'build.gradle', type: 'ARES_1' },
        { location: 'build.gradle', type: 'NO_ARES_VERSION_PINNED' },
    ],
});

type SandboxApiMock = {
    getFrameworkVersions: ReturnType<typeof vi.fn>;
    getConfiguration: ReturnType<typeof vi.fn>;
    activate: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
    updateFrameworkVersion: ReturnType<typeof vi.fn>;
    addMissingTestClasses: ReturnType<typeof vi.fn>;
};

const createApiMock = (): SandboxApiMock => ({
    getFrameworkVersions: vi.fn().mockReturnValue(of(VERSIONS)),
    getConfiguration: vi.fn().mockReturnValue(of(inactiveConfig())),
    activate: vi.fn(),
    deactivate: vi.fn(),
    updateFrameworkVersion: vi.fn(),
    addMissingTestClasses: vi.fn(),
});

const javaExercise = (id?: number, projectType = ProjectType.PLAIN_GRADLE): ProgrammingExercise => {
    const exercise = new ProgrammingExercise(undefined, undefined);
    exercise.id = id;
    exercise.programmingLanguage = ProgrammingLanguage.JAVA;
    exercise.projectType = projectType;
    return exercise;
};

/**
 * Logic-focused spec: the card's status machine and its server contract (getConfiguration, activate, deactivate, updateFrameworkVersion,
 * addMissingTestClasses), driven through a mocked generated {@link SandboxApi}. The template is overridden to empty so the transitions are asserted on the
 * card's own signals, independent of the design-system child components.
 */
describe('SandboxConfigurationCardComponent', () => {
    let fixture: ComponentFixture<SandboxConfigurationCardComponent>;
    let comp: SandboxConfigurationCardComponent;
    let api: SandboxApiMock;

    beforeEach(() => {
        api = createApiMock();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), { provide: TranslateService, useClass: MockTranslateService }, { provide: SandboxApi, useValue: api }],
        });
        TestBed.overrideComponent(SandboxConfigurationCardComponent, { set: { template: '<div></div>' } });
        createComponent();
    });

    function createComponent(): void {
        fixture = TestBed.createComponent(SandboxConfigurationCardComponent);
        comp = fixture.componentInstance;
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

    /** Deactivates and confirms the dialog. */
    function deactivateConfirmed(): void {
        const request = captureConfirmation();
        comp.onToggleChanged(false);
        request().accept();
    }

    function initEdit(initial: SandboxConfiguration, language = ProgrammingLanguage.JAVA): void {
        api.getConfiguration.mockReturnValue(of(initial));
        const exercise = javaExercise(42);
        exercise.programmingLanguage = language;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.detectChanges();
    }

    it('hides the card for the black-box project type, which runs its tests without Ares, and for other languages', () => {
        fixture.componentRef.setInput('programmingExercise', javaExercise(42, ProjectType.MAVEN_BLACKBOX));
        fixture.detectChanges();
        expect(comp.isAvailable()).toBe(false);

        const exercise = javaExercise(42);
        exercise.programmingLanguage = ProgrammingLanguage.C;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.detectChanges();
        expect(comp.isAvailable()).toBe(false);
        expect(api.getConfiguration).not.toHaveBeenCalled();
        expect(api.getFrameworkVersions).not.toHaveBeenCalled();

        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.detectChanges();
        expect(comp.isAvailable()).toBe(true);
        expect(api.getConfiguration).toHaveBeenCalledExactlyOnceWith(42);
        expect(api.getFrameworkVersions).toHaveBeenCalledExactlyOnceWith(42);
    });

    it('reads nothing for an exercise that has not been saved yet', () => {
        fixture.componentRef.setInput('programmingExercise', javaExercise());
        fixture.detectChanges();
        expect(comp.isAvailable()).toBe(false);
        expect(api.getConfiguration).not.toHaveBeenCalled();
    });

    it('loads the persisted config from the server in edit mode', () => {
        initEdit(configuredConfig('3.3.0'));
        expect(api.getConfiguration).toHaveBeenCalledWith(42);
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        expect(comp.state().frameworkVersion).toBe('3.3.0');
    });

    it('activates with the pinned version while the versions are unknown', () => {
        api.getFrameworkVersions.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        // The versions are requested on construction, so re-create the component with the failing request.
        fixture.destroy();
        createComponent();
        initEdit({ status: 'INACTIVE', frameworkVersion: '2.0.0' });
        api.activate.mockReturnValue(of(configuredConfig('2.0.0')));

        comp.onToggleChanged(true);

        expect(api.activate).toHaveBeenCalledWith(42, { frameworkVersion: '2.0.0' });
    });

    it('ignores choosing a version that is not selectable', () => {
        initEdit(configuredConfig('2.0.0'));
        comp.onFrameworkVersionChanged('2.0.0');
        expect(api.updateFrameworkVersion).not.toHaveBeenCalled();
    });

    it('activates an exercise that pins a no longer selectable version with the template version', () => {
        initEdit({ status: 'INACTIVE', frameworkVersion: '2.0.0' });
        api.activate.mockReturnValue(of(configuredConfig('3.2.2')));
        comp.onToggleChanged(true);
        expect(api.activate).toHaveBeenCalledWith(42, { frameworkVersion: '3.2.2' });
    });

    it('activates with the newest selectable version when the exercise and the template both pin unsupported versions', () => {
        api.getFrameworkVersions.mockReturnValue(of({ selectableVersions: ['3.4.1', '3.3.0'], templateVersions: { [ProjectType.PLAIN_GRADLE]: '2.0.0' } }));
        fixture.destroy();
        createComponent();
        initEdit({ status: 'INACTIVE', frameworkVersion: '1.9.0' });
        api.activate.mockReturnValue(of(configuredConfig('3.4.1')));

        comp.onToggleChanged(true);

        expect(api.activate).toHaveBeenCalledWith(42, { frameworkVersion: '3.4.1' });
    });

    it('labels the newest version and marks a no longer supported version the exercise pins', () => {
        initEdit(configuredConfig('2.0.0'));
        expect(comp.frameworkVersions().map((option) => option.version)).toEqual(['3.4.1', '3.3.0', '3.2.2', '2.0.0']);
        expect(comp.frameworkVersions()[0].label).toBe('3.4.1 artemisApp.sandbox.latestSuffix');
        expect(comp.frameworkVersions()[1].label).toBe('3.3.0');
        expect(comp.frameworkVersions()[3].label).toBe('2.0.0 artemisApp.sandbox.noLongerSupportedSuffix');
    });

    describe('versions that do not accept the policy (B10)', () => {
        const INCOMPATIBILITY = 'SecurityPolicy.yaml: regardingTheSupervisedCode.theFollowingTestBehaviorIsConfigured.regardingStrictTimeouts';
        const withCompatibilities = (): SandboxConfiguration => ({
            ...configuredConfig('3.4.1'),
            versionCompatibilities: [
                { version: '3.4.1', verdict: 'COMPATIBLE' },
                { version: '3.3.0', verdict: 'INCOMPATIBLE', incompatibilities: [INCOMPATIBILITY] },
                { version: '3.2.2', verdict: 'UNVERIFIED' },
            ],
        });

        it('marks incompatible and unverified versions in the dropdown', () => {
            initEdit(withCompatibilities());
            expect(comp.frameworkVersions().map((option) => option.label)).toEqual([
                '3.4.1 artemisApp.sandbox.latestSuffix',
                '3.3.0 artemisApp.sandbox.incompatibleSuffix',
                '3.2.2 artemisApp.sandbox.unverifiedSuffix',
            ]);
            expect(comp.incompatibleVersions().map((compatibility) => compatibility.version)).toEqual(['3.3.0']);
        });

        it('does not request a version that does not accept the policy, but still allows the others', () => {
            initEdit(withCompatibilities());
            api.updateFrameworkVersion.mockReturnValue(of(configuredConfig('3.2.2')));

            comp.onFrameworkVersionChanged('3.3.0');
            expect(api.updateFrameworkVersion).not.toHaveBeenCalled();
            expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);

            comp.onFrameworkVersionChanged('3.2.2');
            expect(api.updateFrameworkVersion).toHaveBeenCalledWith(42, { frameworkVersion: '3.2.2' });
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
            api.updateFrameworkVersion.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'error.sandboxVersionIncompatible', params } })));

            comp.onFrameworkVersionChanged('3.3.0');

            expect(instant).toHaveBeenCalledWith('error.sandboxVersionIncompatible', params);
            expect(comp.state().errorDetail).toBe('Ares 3.3.0 does not accept it');
        });
    });

    it('disables the version selector when no versions are known', () => {
        api.getFrameworkVersions.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        // The versions are requested on construction, so re-create the component with the failing request.
        fixture.destroy();
        createComponent();
        initEdit({ status: 'CONFIGURED' });
        expect(comp.isVersionSelectDisabled()).toBe(true);
    });

    it('edit mode activation goes GENERATING then CONFIGURED', () => {
        initEdit(inactiveConfig());
        const activate$ = new Subject<SandboxConfiguration>();
        api.activate.mockReturnValue(activate$.asObservable());
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SandboxCardStatus.GENERATING);
        expect(comp.isBusy()).toBe(true);
        expect(api.activate).toHaveBeenCalledWith(42, { frameworkVersion: '3.4.1' });
        activate$.next(configuredConfig());
        activate$.complete();
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
    });

    it('edit mode deactivation asks for confirmation, then goes DELETING then INACTIVE', () => {
        initEdit(configuredConfig());
        const deactivate$ = new Subject<SandboxConfiguration>();
        api.deactivate.mockReturnValue(deactivate$.asObservable());
        const request = captureConfirmation();

        comp.onToggleChanged(false);
        expect(api.deactivate).not.toHaveBeenCalled();
        expect(comp.isToggleOn()).toBe(false);

        request().accept();
        expect(comp.status()).toBe(SandboxCardStatus.DELETING);
        expect(api.deactivate).toHaveBeenCalledOnce();
        expect(api.deactivate).toHaveBeenCalledWith(42);
        deactivate$.next(inactiveConfig());
        deactivate$.complete();
        expect(comp.status()).toBe(SandboxCardStatus.INACTIVE);
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

        expect(request().message).toBe('artemisApp.sandbox.deactivateConfirmation.message artemisApp.sandbox.deactivateConfirmation.aresLibraryStays');
        expect(instant).toHaveBeenCalledWith('artemisApp.sandbox.deactivateConfirmation.aresLibraryStays', {
            apis: 'structural.ClassTestProvider, StrictTimeout',
        });
    });

    it('leaves the library sentence out of the confirmation when the tests use only the sandbox', () => {
        initEdit(configuredConfig());
        const request = captureConfirmation();

        comp.onToggleChanged(false);

        expect(request().message).toBe('artemisApp.sandbox.deactivateConfirmation.message');
    });

    it('cancelling the deactivation confirmation sends no request and turns the switch back on', () => {
        initEdit(configuredConfig());
        const request = captureConfirmation();

        comp.onToggleChanged(false);
        expect(comp.isToggleOn()).toBe(false);
        request().reject!();

        expect(api.deactivate).not.toHaveBeenCalled();
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        expect(comp.isToggleOn()).toBe(true);
    });

    it('drops to ERROR on activation failure, then retry re-runs it to CONFIGURED', () => {
        initEdit(inactiveConfig());
        api.activate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SandboxCardStatus.ERROR);
        api.activate.mockReturnValueOnce(of(configuredConfig()));
        comp.onRetry();
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        expect(api.activate).toHaveBeenCalledTimes(2);
    });

    it('disables the version selector in ERROR so the shown version cannot diverge from the retried operation', () => {
        initEdit(configuredConfig());
        expect(comp.isVersionSelectDisabled()).toBe(false);
        api.updateFrameworkVersion.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onFrameworkVersionChanged('3.3.0');
        expect(comp.status()).toBe(SandboxCardStatus.ERROR);
        expect(comp.isVersionSelectDisabled()).toBe(true);
    });

    it('retry re-runs the operation that actually failed (deactivation, not activation)', () => {
        initEdit(configuredConfig());
        api.deactivate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        deactivateConfirmed();
        expect(comp.status()).toBe(SandboxCardStatus.ERROR);
        api.deactivate.mockReturnValueOnce(of(inactiveConfig()));
        comp.onRetry();
        expect(comp.status()).toBe(SandboxCardStatus.INACTIVE);
        expect(api.deactivate).toHaveBeenCalledTimes(2);
        expect(api.activate).not.toHaveBeenCalled();
    });

    it('drops the failed operation of the previous exercise and loads the new one when the card is reused for another exercise', () => {
        initEdit(configuredConfig());
        api.deactivate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        deactivateConfirmed();
        expect(comp.status()).toBe(SandboxCardStatus.ERROR);

        api.getConfiguration.mockReturnValue(of(inactiveConfig()));
        fixture.componentRef.setInput('programmingExercise', javaExercise(43));
        fixture.detectChanges();

        expect(api.getConfiguration).toHaveBeenLastCalledWith(43);
        expect(comp.status()).toBe(SandboxCardStatus.INACTIVE);
        comp.onRetry();
        expect(api.deactivate).toHaveBeenCalledOnce();
        expect(api.deactivate).toHaveBeenCalledWith(42);
    });

    it('closes an open deactivation confirmation and never deactivates the previous exercise when the card is reused for another one', () => {
        initEdit(configuredConfig());
        const request = captureConfirmation();
        const close = vi.spyOn(confirmationService(), 'close');
        comp.onToggleChanged(false);

        api.getConfiguration.mockReturnValue(of(configuredConfig()));
        fixture.componentRef.setInput('programmingExercise', javaExercise(43));
        fixture.detectChanges();

        expect(close).toHaveBeenCalledOnce();
        expect(comp.isToggleOn()).toBe(true);
        request().accept();
        expect(api.deactivate).not.toHaveBeenCalled();
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
    });

    it('ignores a late result of an earlier visit after returning to the same exercise', () => {
        initEdit(configuredConfig());
        const lateDeactivation = new Subject<SandboxConfiguration>();
        api.deactivate.mockReturnValueOnce(lateDeactivation);
        deactivateConfirmed();

        api.getConfiguration.mockReturnValue(of(configuredConfig()));
        fixture.componentRef.setInput('programmingExercise', javaExercise(43));
        fixture.detectChanges();
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.detectChanges();
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);

        lateDeactivation.next(inactiveConfig());

        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
    });

    it('never lets a late load overwrite a newer one', () => {
        const firstLoad = new Subject<SandboxConfiguration>();
        api.getConfiguration.mockReturnValueOnce(firstLoad);
        const exercise = javaExercise(42);
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.detectChanges();

        api.getConfiguration.mockReturnValue(of(inactiveConfig()));
        fixture.componentRef.setInput('programmingExercise', javaExercise(43));
        fixture.detectChanges();
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.detectChanges();
        expect(comp.status()).toBe(SandboxCardStatus.INACTIVE);

        firstLoad.next(configuredConfig());

        expect(comp.status()).toBe(SandboxCardStatus.INACTIVE);
    });

    it('ignores a late result of an operation on the previous exercise', () => {
        initEdit(configuredConfig());
        const result = new Subject<SandboxConfiguration>();
        api.deactivate.mockReturnValueOnce(result);
        deactivateConfirmed();

        api.getConfiguration.mockReturnValue(of(configuredConfig('3.3.0')));
        fixture.componentRef.setInput('programmingExercise', javaExercise(43));
        fixture.detectChanges();
        result.next(inactiveConfig());

        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        expect(comp.state().frameworkVersion).toBe('3.3.0');
    });

    it('re-syncs when the framework version changes on an active exercise', () => {
        initEdit(configuredConfig('3.4.1'));
        const update$ = new Subject<SandboxConfiguration>();
        api.updateFrameworkVersion.mockReturnValue(update$.asObservable());
        comp.onFrameworkVersionChanged('3.3.0');
        expect(comp.status()).toBe(SandboxCardStatus.GENERATING);
        expect(api.updateFrameworkVersion).toHaveBeenCalledWith(42, { frameworkVersion: '3.3.0' });
        update$.next(configuredConfig('3.3.0'));
        update$.complete();
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        expect(comp.state().frameworkVersion).toBe('3.3.0');
    });

    it('locks the version selector of an inactive exercise, whose version can only change while active, and shows the version the repository pins', () => {
        initEdit(inactiveConfig());
        expect(comp.isVersionSelectDisabled()).toBe(true);

        comp.onFrameworkVersionChanged('3.3.0');

        expect(comp.state().frameworkVersion).toBe('3.4.1');
        expect(comp.status()).toBe(SandboxCardStatus.INACTIVE);
        expect(api.updateFrameworkVersion).not.toHaveBeenCalled();
    });

    it('on ERROR keeps the toggle off after a failed activation', () => {
        initEdit(inactiveConfig());
        api.activate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SandboxCardStatus.ERROR);
        expect(comp.isToggleOn()).toBe(false);
    });

    it('on ERROR keeps the toggle on after a failed deactivation', () => {
        initEdit(configuredConfig());
        api.deactivate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        deactivateConfirmed();
        expect(comp.status()).toBe(SandboxCardStatus.ERROR);
        expect(comp.isToggleOn()).toBe(true);
    });

    it('surfaces a load error instead of a misleading INACTIVE, and recovers on retry', () => {
        api.getConfiguration.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.detectChanges();
        expect(comp.loadFailed()).toBe(true);

        api.getConfiguration.mockReturnValue(of(configuredConfig()));
        comp.retryLoadConfiguration();
        expect(comp.loadFailed()).toBe(false);
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
    });

    it('blocks the toggle and version selector while the edit-mode config is loading', () => {
        const load$ = new Subject<SandboxConfiguration>();
        api.getConfiguration.mockReturnValue(load$.asObservable());
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.detectChanges();
        expect(comp.isBusy()).toBe(true);
        expect(comp.isVersionSelectDisabled()).toBe(true);

        load$.next(configuredConfig());
        load$.complete();
        expect(comp.isBusy()).toBe(false);
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
    });

    it('ignores a late config load while an operation is in flight so it cannot overwrite the result', () => {
        const load$ = new Subject<SandboxConfiguration>();
        api.getConfiguration.mockReturnValue(load$.asObservable());
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.detectChanges();

        const activate$ = new Subject<SandboxConfiguration>();
        api.activate.mockReturnValue(activate$.asObservable());
        comp.onToggleChanged(true);

        load$.next(inactiveConfig());
        load$.complete();
        expect(comp.status()).toBe(SandboxCardStatus.GENERATING);

        activate$.next(configuredConfig());
        activate$.complete();
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
    });

    describe('MISCONFIGURED in edit mode', () => {
        it('repairs by activating with the current version', () => {
            initEdit(misconfiguredConfig());
            api.activate.mockReturnValue(of(configuredConfig('3.3.0')));
            expect(comp.showToggle()).toBe(false);
            expect(comp.isBusy()).toBe(false);

            comp.onRepair();

            expect(api.activate).toHaveBeenCalledExactlyOnceWith(42, { frameworkVersion: '3.3.0' });
            expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        });

        it('deactivates only after confirmation', () => {
            initEdit(misconfiguredConfig());
            api.deactivate.mockReturnValue(of(inactiveConfig()));
            const request = captureConfirmation();

            comp.onDeactivate();
            expect(api.deactivate).not.toHaveBeenCalled();
            request().accept();

            expect(api.deactivate).toHaveBeenCalledExactlyOnceWith(42);
        });

        it('explains which policy file breaks which tests and whether it can be repaired', () => {
            initEdit(misconfiguredConfig('UNPARSEABLE'));
            expect(comp.problemMessages()).toEqual([
                { key: 'artemisApp.sandbox.misconfigured.UNPARSEABLE', params: { path: 'SecurityPolicy.yaml', classes: 'de.x.ATest, de.x.BTest' } },
            ]);
            expect(comp.hasUnrepairableProblem()).toBe(true);
        });

        it('treats a missing policy file as repairable', () => {
            initEdit(misconfiguredConfig('MISSING'));
            expect(comp.hasUnrepairableProblem()).toBe(false);
        });

        it('repairs with a newly chosen version, because that version may accept a policy the pinned one refuses', () => {
            initEdit(misconfiguredConfig('UNPARSEABLE'));
            const activate$ = new Subject<SandboxConfiguration>();
            api.activate.mockReturnValue(activate$.asObservable());

            comp.onFrameworkVersionChanged('3.4.1');

            expect(api.activate).toHaveBeenCalledExactlyOnceWith(42, { frameworkVersion: '3.4.1' });
            expect(comp.status()).toBe(SandboxCardStatus.GENERATING);
            activate$.next(configuredConfig('3.4.1'));
            activate$.complete();
            expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
            expect(comp.state().frameworkVersion).toBe('3.4.1');
        });

        it('keeps the pinned version when the chosen one cannot repair the policy either', () => {
            initEdit(misconfiguredConfig('UNPARSEABLE'));
            api.activate.mockReturnValue(throwError(() => new Error('STILL_INVALID')));

            comp.onFrameworkVersionChanged('3.4.1');

            expect(comp.status()).toBe(SandboxCardStatus.MISCONFIGURED);
            expect(comp.state().frameworkVersion).toBe('3.3.0');
            expect(comp.state().errorDetail).toBe('STILL_INVALID');
        });

        it('stays MISCONFIGURED after a failed repair, so Deactivate remains available', () => {
            initEdit(misconfiguredConfig('MISSING'));
            api.activate.mockReturnValue(throwError(() => new Error('REPAIR_FAILED')));

            comp.onRepair();

            expect(comp.status()).toBe(SandboxCardStatus.MISCONFIGURED);
            expect(comp.state().errorDetail).toBe('REPAIR_FAILED');
            expect(comp.isBusy()).toBe(false);
        });

        it('stays MISCONFIGURED after a failed deactivation', () => {
            initEdit(misconfiguredConfig('UNPARSEABLE'));
            api.deactivate.mockReturnValue(throwError(() => new Error('DEACTIVATE_FAILED')));
            const request = captureConfirmation();

            comp.onDeactivate();
            request().accept();

            expect(comp.status()).toBe(SandboxCardStatus.MISCONFIGURED);
            expect(comp.state().errorDetail).toBe('DEACTIVATE_FAILED');
        });

        it('clears the error of a failed repair when the next attempt succeeds', () => {
            initEdit(misconfiguredConfig('MISSING'));
            api.activate.mockReturnValueOnce(throwError(() => new Error('REPAIR_FAILED')));
            comp.onRepair();
            api.activate.mockReturnValueOnce(of(configuredConfig('3.3.0')));

            comp.onRepair();

            expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
            expect(comp.state().errorDetail).toBeUndefined();
        });

        describe('error detail of a refused request', () => {
            const refusal = (message: unknown) =>
                new HttpErrorResponse({ status: 400, error: { message }, headers: new HttpHeaders({ 'X-artemisApp-message': 'English text of the server' }) });

            it('shows the translation the server names', () => {
                initEdit(misconfiguredConfig('MISSING'));
                vi.spyOn(TestBed.inject(TranslateService), 'instant').mockReturnValue('Übersetzter Text');
                api.activate.mockReturnValue(throwError(() => refusal('error.sandboxPolicyNotRepairable')));

                comp.onRepair();

                expect(comp.state().errorDetail).toBe('Übersetzter Text');
            });

            it('falls back to the English text if the translation is missing', () => {
                initEdit(misconfiguredConfig('MISSING'));
                vi.spyOn(TestBed.inject(TranslateService), 'instant').mockReturnValue('translation-not-found[error.unknown]');
                api.activate.mockReturnValue(throwError(() => refusal('error.unknown')));

                comp.onRepair();

                expect(comp.state().errorDetail).toBe('English text of the server');
            });

            it('falls back to the English text if the server names no translation', () => {
                initEdit(misconfiguredConfig('MISSING'));
                api.activate.mockReturnValue(throwError(() => refusal(undefined)));

                comp.onRepair();

                expect(comp.state().errorDetail).toBe('English text of the server');
            });
        });
    });

    it('lists the test classes missing in an active policy and the policy files that could not be checked', () => {
        initEdit({
            status: 'CONFIGURED',
            frameworkVersion: '3.4.1',
            disabledTestClasses: ['de.x.OffTest'],
            policyFiles: [
                { policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A'], missingTestClasses: ['de.x.New'] },
                { policyPath: 'Other.yaml', referencedBy: ['de.x.B'], problem: 'TEST_CLASS_LIST_NOT_FOUND' },
            ],
        });
        expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        expect(comp.missingTestClasses()).toEqual(['de.x.New']);
        expect(comp.uncheckedPolicyFiles()).toEqual(['Other.yaml']);
        expect(comp.problemMessages()).toEqual([]);
    });

    describe('adding the discovered test classes (B5)', () => {
        const withMissingClass = (): SandboxConfiguration => ({
            ...configuredConfig(),
            policyFiles: [{ policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A', 'de.x.New'], missingTestClasses: ['de.x.New'] }],
        });

        it('commits the missing test classes through the server and settles on the returned config', () => {
            initEdit(withMissingClass());
            const add$ = new Subject<SandboxConfiguration>();
            api.addMissingTestClasses.mockReturnValue(add$.asObservable());

            comp.onAddMissingTestClasses();

            expect(api.addMissingTestClasses).toHaveBeenCalledWith(42);
            expect(comp.status()).toBe(SandboxCardStatus.GENERATING);
            add$.next({ ...configuredConfig(), policyFiles: [{ policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A', 'de.x.New'] }] });
            add$.complete();
            expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
            expect(comp.missingTestClasses()).toEqual([]);
        });

        it('drops to ERROR if the server refuses, and retry runs the same operation again', () => {
            initEdit(withMissingClass());
            api.addMissingTestClasses.mockReturnValueOnce(throwError(() => new Error('PUSH_FAILED')));

            comp.onAddMissingTestClasses();
            expect(comp.status()).toBe(SandboxCardStatus.ERROR);

            api.addMissingTestClasses.mockReturnValueOnce(of(configuredConfig()));
            comp.onRetry();
            expect(api.addMissingTestClasses).toHaveBeenCalledTimes(2);
            expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        });
    });

    describe('defaults and fallbacks', () => {
        it('activates with the first template version without a project type, and with the newest version without template versions', () => {
            const exercise = javaExercise(42);
            exercise.projectType = undefined;
            api.getConfiguration.mockReturnValue(of({ status: 'INACTIVE' }));
            api.activate.mockReturnValue(of(configuredConfig('3.2.2')));
            fixture.componentRef.setInput('programmingExercise', exercise);
            fixture.detectChanges();
            comp.onToggleChanged(true);
            expect(api.activate).toHaveBeenLastCalledWith(42, { frameworkVersion: '3.2.2' });

            api.getFrameworkVersions.mockReturnValue(of({ selectableVersions: ['3.4.1'] }));
            fixture.destroy();
            createComponent();
            fixture.componentRef.setInput('programmingExercise', exercise);
            fixture.detectChanges();
            comp.onToggleChanged(true);
            expect(api.activate).toHaveBeenLastCalledWith(42, { frameworkVersion: '3.4.1' });
        });

        it('activates nothing in edit mode while neither the exercise nor the server names a version', () => {
            api.getFrameworkVersions.mockReturnValue(throwError(() => new Error('VERSIONS_FAILED')));
            fixture.destroy();
            createComponent();
            initEdit({ status: 'INACTIVE' });

            comp.onToggleChanged(true);

            expect(api.activate).not.toHaveBeenCalled();
            expect(comp.status()).toBe(SandboxCardStatus.INACTIVE);
        });

        it('treats a policy file without a problem as repairable', () => {
            initEdit({ status: 'CONFIGURED', frameworkVersion: '3.4.1', policyFiles: [{ policyPath: 'SecurityPolicy.yaml' }] });

            expect(comp.hasUnrepairableProblem()).toBe(false);
        });

        it('keeps the switch on while an activation is in flight', () => {
            initEdit(inactiveConfig());
            api.activate.mockReturnValue(new Subject<SandboxConfiguration>());

            comp.onToggleChanged(true);

            expect(comp.status()).toBe(SandboxCardStatus.GENERATING);
            expect(comp.isToggleOn()).toBe(true);
        });

        it('ignores a version picked in ERROR, so Retry runs exactly what failed', () => {
            initEdit(inactiveConfig());
            api.activate.mockReturnValue(throwError(() => new Error('ACTIVATION_FAILED')));
            comp.onToggleChanged(true);
            expect(comp.status()).toBe(SandboxCardStatus.ERROR);

            comp.onFrameworkVersionChanged('3.3.0');

            expect(comp.state().frameworkVersion).toBe('3.4.1');
            expect(api.updateFrameworkVersion).not.toHaveBeenCalled();
            expect(api.activate).toHaveBeenCalledOnce();
        });
    });

    describe('loading', () => {
        it('loads nothing in edit mode for an exercise without id, also on retry', () => {
            fixture.componentRef.setInput('programmingExercise', javaExercise());
            fixture.detectChanges();

            comp.retryLoadConfiguration();

            expect(api.getConfiguration).not.toHaveBeenCalled();
        });

        it('ignores a late load error of the previous exercise', () => {
            const firstLoad$ = new Subject<SandboxConfiguration>();
            api.getConfiguration.mockReturnValueOnce(firstLoad$.asObservable());
            fixture.componentRef.setInput('programmingExercise', javaExercise(42));
            fixture.detectChanges();

            api.getConfiguration.mockReturnValueOnce(of(configuredConfig()));
            fixture.componentRef.setInput('programmingExercise', javaExercise(43));
            fixture.detectChanges();
            firstLoad$.error(new Error('LATE_FAILURE'));

            expect(comp.loadFailed()).toBe(false);
            expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
        });
    });

    describe('the message of a failed operation', () => {
        it.each([
            ['the message header', new HttpErrorResponse({ status: 400, headers: new HttpHeaders({ 'X-artemisApp-message': 'From the header' }) }), 'From the header'],
            ['the title', new HttpErrorResponse({ status: 400, error: { title: 'The title' } }), 'The title'],
            ['the detail', new HttpErrorResponse({ status: 400, error: { detail: 'The detail' } }), 'The detail'],
            ['a message that names no translation', new HttpErrorResponse({ status: 400, error: { message: 'Plain text' } }), 'Plain text'],
            ['an error', new Error('Something broke'), 'Something broke'],
            ['anything else', 'just text', 'just text'],
        ])('uses %s', (_description, error, expected) => {
            initEdit(inactiveConfig());
            api.activate.mockReturnValue(throwError(() => error));

            comp.onToggleChanged(true);

            expect(comp.status()).toBe(SandboxCardStatus.ERROR);
            expect(comp.state().errorDetail).toBe(expected);
        });

        it('falls back to the message of the HTTP error', () => {
            initEdit(inactiveConfig());
            const error = new HttpErrorResponse({ status: 500, url: 'api/x' });
            api.activate.mockReturnValue(throwError(() => error));

            comp.onToggleChanged(true);

            expect(comp.state().errorDetail).toBe(error.message);
        });

        it('ignores a late failure of an operation on the previous exercise', () => {
            initEdit(inactiveConfig());
            const activation$ = new Subject<SandboxConfiguration>();
            api.activate.mockReturnValue(activation$.asObservable());
            comp.onToggleChanged(true);

            api.getConfiguration.mockReturnValue(of(configuredConfig()));
            fixture.componentRef.setInput('programmingExercise', javaExercise(43));
            fixture.detectChanges();
            activation$.error(new Error('LATE_FAILURE'));

            expect(comp.status()).toBe(SandboxCardStatus.CONFIGURED);
            expect(comp.state().errorDetail).toBeUndefined();
        });
    });
});

/** Rendering spec: the real template, for the states whose text is derived from the configuration rather than only the status. */
describe('SandboxConfigurationCardComponent rendering', () => {
    let fixture: ComponentFixture<SandboxConfigurationCardComponent>;
    let api: SandboxApiMock;

    beforeEach(() => {
        api = createApiMock();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), { provide: TranslateService, useClass: MockTranslateService }, { provide: SandboxApi, useValue: api }],
        });
        fixture = TestBed.createComponent(SandboxConfigurationCardComponent);
    });

    function renderEdit(config: SandboxConfiguration): HTMLElement {
        api.getConfiguration.mockReturnValue(of(config));
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    const query = (element: HTMLElement, testId: string) => element.querySelector(`[data-testid="${testId}"]`);

    it('renders a configured sandbox as CONFIGURED, never as confirmed ready, because no Ares build result confirms it yet (B11)', () => {
        const element = renderEdit(configuredConfig('3.3.0'));
        expect(query(element, 'sandbox-card')?.classList).toContain('sf-card--configured');
        expect(query(element, 'sandbox-status-pill')?.classList).toContain('sf-pill--configured');
        expect(element.querySelector('[class*="--active"]')).toBeNull();
    });

    it('lists what each version that does not accept the policy does not accept, and nothing without a checked policy (B10)', () => {
        const element = renderEdit({
            ...configuredConfig('3.4.1'),
            versionCompatibilities: [
                { version: '3.4.1', verdict: 'COMPATIBLE' },
                { version: '3.3.0', verdict: 'INCOMPATIBLE', incompatibilities: ['SecurityPolicy.yaml: a.b'] },
            ],
        });
        const items = query(element, 'sandbox-incompatible-versions')?.querySelectorAll('li');
        expect(items).toHaveLength(1);
        expect(items?.[0].textContent).toContain('artemisApp.sandbox.versionIncompatible');
    });

    it('offers to add the missing test classes in edit mode only while some are missing (B5)', () => {
        const element = renderEdit({
            ...configuredConfig('3.4.1'),
            policyFiles: [{ policyPath: 'SecurityPolicy.yaml', referencedBy: ['de.x.A', 'de.x.New'], missingTestClasses: ['de.x.New'] }],
        });
        expect(query(element, 'sandbox-add-test-classes')).not.toBeNull();
    });

    it('does not offer to add test classes when every test class is listed', () => {
        const element = renderEdit(configuredConfig('3.4.1'));
        expect(query(element, 'sandbox-add-test-classes')).toBeNull();
    });

    it('does not let the real select choose a version that does not accept the policy, so the shown version stays the saved one (B10)', async () => {
        const element = renderEdit({
            ...configuredConfig('3.4.1'),
            versionCompatibilities: [
                { version: '3.4.1', verdict: 'COMPATIBLE' },
                { version: '3.3.0', verdict: 'INCOMPATIBLE', incompatibilities: ['SecurityPolicy.yaml: a.b'] },
                { version: '3.2.2', verdict: 'COMPATIBLE' },
            ],
        });
        // ngModel writes its value to the select asynchronously.
        await fixture.whenStable();
        fixture.detectChanges();
        const trigger = query(element, 'sandbox-framework-version-select')?.querySelector('button[aria-haspopup="listbox"]') as HTMLButtonElement;
        const shownVersion = () => trigger.textContent?.trim();
        expect(shownVersion()).toBe('3.4.1 artemisApp.sandbox.latestSuffix');

        trigger.click();
        fixture.detectChanges();
        const options = Array.from(document.querySelectorAll('[role="option"]')) as HTMLElement[];
        const incompatible = options.find((option) => option.textContent?.includes('3.3.0'))!;
        expect(incompatible.getAttribute('aria-disabled')).toBe('true');
        incompatible.click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(api.updateFrameworkVersion).not.toHaveBeenCalled();
        expect(shownVersion()).toBe('3.4.1 artemisApp.sandbox.latestSuffix');
        expect(fixture.componentInstance.state().frameworkVersion).toBe('3.4.1');
    });

    it('shows no list of incompatible versions when the server checked no policy file', () => {
        const element = renderEdit(configuredConfig('3.4.1'));
        expect(query(element, 'sandbox-incompatible-versions')).toBeNull();
    });

    it('renders MISCONFIGURED with the broken policy file, Repair and Deactivate, and no switch', () => {
        const element = renderEdit(misconfiguredConfig('TOO_LARGE'));
        expect(query(element, 'sandbox-card')?.classList).toContain('sf-card--misconfigured');
        expect(query(element, 'sandbox-misconfigured-problems')?.textContent).toContain('artemisApp.sandbox.misconfigured.TOO_LARGE');
        expect(query(element, 'sandbox-not-repairable')).not.toBeNull();
        expect(query(element, 'sandbox-repair')).not.toBeNull();
        expect(query(element, 'sandbox-deactivate')).not.toBeNull();
        expect(query(element, 'sandbox-activate-toggle')).toBeNull();
        expect(query(element, 'sandbox-framework-version-select')).not.toBeNull();
    });

    it('disables Repair for a policy file it cannot repair and keeps Deactivate enabled', () => {
        const element = renderEdit(misconfiguredConfig('UNPARSEABLE'));
        expect((query(element, 'sandbox-repair') as HTMLButtonElement).disabled).toBe(true);
        expect((query(element, 'sandbox-deactivate') as HTMLButtonElement).disabled).toBe(false);
    });

    it('enables Repair for a missing policy file', () => {
        const element = renderEdit(misconfiguredConfig('MISSING'));
        expect((query(element, 'sandbox-repair') as HTMLButtonElement).disabled).toBe(false);
    });

    it('sends the version chosen in the real select of a misconfigured exercise to the server, even though Repair is disabled', async () => {
        const element = renderEdit(misconfiguredConfig('UNPARSEABLE'));
        api.activate.mockReturnValue(of(configuredConfig('3.4.1')));
        await fixture.whenStable();
        fixture.detectChanges();
        expect((query(element, 'sandbox-repair') as HTMLButtonElement).disabled).toBe(true);

        (query(element, 'sandbox-framework-version-select')?.querySelector('button[aria-haspopup="listbox"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        const option = (Array.from(document.querySelectorAll('[role="option"]')) as HTMLElement[]).find((candidate) => candidate.textContent?.includes('3.4.1'))!;
        option.click();
        fixture.detectChanges();

        expect(api.activate).toHaveBeenCalledExactlyOnceWith(42, { frameworkVersion: '3.4.1' });
        expect(query(element, 'sandbox-card')?.classList).toContain('sf-card--configured');
    });

    it('shows why a repair failed and keeps Repair and Deactivate', () => {
        const element = renderEdit(misconfiguredConfig('MISSING'));
        api.activate.mockReturnValue(throwError(() => new Error('REPAIR_FAILED')));

        (query(element, 'sandbox-repair') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(query(element, 'sandbox-error-detail')?.textContent).toContain('REPAIR_FAILED');
        expect(query(element, 'sandbox-repair')).not.toBeNull();
        expect(query(element, 'sandbox-deactivate')).not.toBeNull();
        // Retry re-runs the failed repair, which the server may already have pushed before it failed.
        expect(query(element, 'sandbox-retry')).not.toBeNull();
    });

    it('retries a failed repair with its own version, not with the restored one', () => {
        const element = renderEdit(misconfiguredConfig('MISSING'));
        expect(query(element, 'sandbox-retry')).toBeNull();
        api.activate.mockReturnValueOnce(throwError(() => new Error('PROCESSING_FAILED')));
        fixture.componentInstance.onFrameworkVersionChanged('3.4.1');
        fixture.detectChanges();
        expect(fixture.componentInstance.state().frameworkVersion).toBe('3.3.0');

        api.activate.mockReturnValueOnce(of(configuredConfig('3.4.1')));
        (query(element, 'sandbox-retry') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(api.activate).toHaveBeenLastCalledWith(42, { frameworkVersion: '3.4.1' });
        expect(fixture.componentInstance.status()).toBe(SandboxCardStatus.CONFIGURED);
        expect(query(element, 'sandbox-retry')).toBeNull();
    });

    it('shows that the config is loading instead of the placeholder status', () => {
        const load$ = new Subject<SandboxConfiguration>();
        api.getConfiguration.mockReturnValue(load$);
        fixture.componentRef.setInput('programmingExercise', javaExercise(42));
        fixture.detectChanges();
        const element = fixture.nativeElement as HTMLElement;

        expect(query(element, 'sandbox-loading')).not.toBeNull();
        expect(query(element, 'sandbox-card')?.classList).toContain('sf-card--loading');
        expect(query(element, 'sandbox-status-text')?.textContent).not.toContain('configuredEdit');
        expect(query(element, 'sandbox-sync-line')?.textContent).toContain('sync.loading');

        load$.next(inactiveConfig());
        fixture.detectChanges();

        expect(query(element, 'sandbox-loading')).toBeNull();
        expect(query(element, 'sandbox-card')?.classList).toContain('sf-card--inactive');
    });

    it('renders UNSUPPORTED with its reasons and neither a switch nor a version select', () => {
        const element = renderEdit(unsupportedConfig());
        expect(query(element, 'sandbox-card')?.classList).toContain('sf-card--unsupported');
        const reasons = query(element, 'sandbox-unsupported-reasons')?.querySelectorAll('li');
        expect(reasons).toHaveLength(2);
        expect(reasons?.[0].textContent).toContain('artemisApp.sandbox.unsupportedReason.ARES_1');
        expect(query(element, 'sandbox-activate-toggle')).toBeNull();
        expect(query(element, 'sandbox-framework-version-select')).toBeNull();
        expect(query(element, 'sandbox-repair')).toBeNull();
    });
});
