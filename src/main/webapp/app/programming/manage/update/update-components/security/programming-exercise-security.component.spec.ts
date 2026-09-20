import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { SecurityActivationStatus, SecurityFrameworkConfig } from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
import { ProgrammingExerciseSecurityComponent } from 'app/programming/manage/update/update-components/security/programming-exercise-security.component';

const VERSIONS = [
    { version: '3.4.1', label: '3.4.1', latest: true },
    { version: '3.3.0', label: '3.3.0' },
    { version: '3.2.2', label: '3.2.2' },
];

const inactiveConfig = (): SecurityFrameworkConfig => ({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '3.4.1' });
const activeConfig = (version = '3.4.1', hash = 'abc1234'): SecurityFrameworkConfig => ({
    status: SecurityActivationStatus.ACTIVE,
    frameworkVersion: version,
    lastCommitHash: hash,
    lastSyncedAt: '2026-09-15T10:00:00Z',
});

/**
 * Logic-focused spec: the component's status machine and its single backend contract (getConfig /
 * activate / deactivate / updateFrameworkVersion), driven through a fully mocked
 * {@link SecurityFrameworkService}. The template is overridden to empty so the transitions are asserted
 * on the component's own signals, independent of the design-system child components.
 */
describe('ProgrammingExerciseSecurityComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseSecurityComponent>;
    let comp: ProgrammingExerciseSecurityComponent;
    let service: {
        getFrameworkVersions: ReturnType<typeof vi.fn>;
        getInitialConfigForCreate: ReturnType<typeof vi.fn>;
        getConfig: ReturnType<typeof vi.fn>;
        activate: ReturnType<typeof vi.fn>;
        deactivate: ReturnType<typeof vi.fn>;
        updateFrameworkVersion: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        service = {
            getFrameworkVersions: vi.fn().mockReturnValue(VERSIONS),
            getInitialConfigForCreate: vi.fn().mockReturnValue(inactiveConfig()),
            getConfig: vi.fn().mockReturnValue(of(inactiveConfig())),
            activate: vi.fn(),
            deactivate: vi.fn(),
            updateFrameworkVersion: vi.fn(),
        };
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), { provide: TranslateService, useClass: MockTranslateService }, { provide: SecurityFrameworkService, useValue: service }],
        });
        TestBed.overrideComponent(ProgrammingExerciseSecurityComponent, { set: { template: '<div></div>' } });
        fixture = TestBed.createComponent(ProgrammingExerciseSecurityComponent);
        comp = fixture.componentInstance;
    });

    function initCreate(language = ProgrammingLanguage.JAVA): void {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.programmingLanguage = language;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('selectedProgrammingLanguage', language);
        fixture.detectChanges();
    }

    function initEdit(initial: SecurityFrameworkConfig, language = ProgrammingLanguage.JAVA): void {
        service.getConfig.mockReturnValue(of(initial));
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 42;
        exercise.programmingLanguage = language;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('selectedProgrammingLanguage', language);
        fixture.detectChanges();
    }

    it('shows the card only for supported languages (Java)', () => {
        initCreate(ProgrammingLanguage.JAVA);
        expect(comp.isSupportedLanguage()).toBe(true);
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
        initEdit(activeConfig('3.3.0', 'aaa1111'));
        expect(service.getConfig).toHaveBeenCalledWith(42);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.config().frameworkVersion).toBe('3.3.0');
    });

    it('create mode stages ACTIVE/INACTIVE locally without calling the backend', () => {
        initCreate();
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        comp.onToggleChanged(false);
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        expect(service.activate).not.toHaveBeenCalled();
        expect(service.deactivate).not.toHaveBeenCalled();
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

    it('edit mode deactivation goes DELETING (with warning) then INACTIVE', () => {
        initEdit(activeConfig());
        const deactivate$ = new Subject<SecurityFrameworkConfig>();
        service.deactivate.mockReturnValue(deactivate$.asObservable());
        comp.onToggleChanged(false);
        expect(comp.status()).toBe(SecurityActivationStatus.DELETING);
        expect(comp.showDeactivateWarning()).toBe(true);
        expect(service.deactivate).toHaveBeenCalledWith(42);
        deactivate$.next(inactiveConfig());
        deactivate$.complete();
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        expect(comp.showDeactivateWarning()).toBe(false);
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

    it('retry re-runs the operation that actually failed (deactivation, not activation)', () => {
        initEdit(activeConfig());
        service.deactivate.mockReturnValueOnce(throwError(() => new Error('SYNC_FAILED')));
        comp.onToggleChanged(false);
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
        update$.next(activeConfig('3.3.0', 'def5678'));
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
        comp.onToggleChanged(false);
        expect(comp.status()).toBe(SecurityActivationStatus.ERROR);
        expect(comp.isToggleOn()).toBe(true);
    });

    it('surfaces a load error instead of a misleading INACTIVE, and recovers on retry', () => {
        service.getConfig.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 42;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.detectChanges();
        expect(comp.loadFailed()).toBe(true);

        service.getConfig.mockReturnValue(of(activeConfig()));
        comp.retryLoadConfig();
        expect(comp.loadFailed()).toBe(false);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
    });

    it('commitStagedActivation is a no-op success when nothing was staged', () => {
        initCreate();
        let result: boolean | undefined;
        comp.commitStagedActivation(99).subscribe((value) => (result = value));
        expect(result).toBe(true);
        expect(service.activate).not.toHaveBeenCalled();
    });

    it('commitStagedActivation persists a staged create-mode activation and reports success', () => {
        initCreate();
        comp.onToggleChanged(true);
        service.activate.mockReturnValue(of(activeConfig()));
        let result: boolean | undefined;
        comp.commitStagedActivation(99).subscribe((value) => (result = value));
        expect(service.activate).toHaveBeenCalledWith(99, '3.4.1');
        expect(result).toBe(true);
    });

    it('commitStagedActivation reports failure (never a false success) when activation fails', () => {
        initCreate();
        comp.onToggleChanged(true);
        service.activate.mockReturnValue(throwError(() => new Error('SYNC_FAILED')));
        let result: boolean | undefined;
        comp.commitStagedActivation(99).subscribe((value) => (result = value));
        expect(result).toBe(false);
    });
});
