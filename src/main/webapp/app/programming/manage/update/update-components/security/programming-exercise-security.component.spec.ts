import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import {
    SecurityActivationStatus,
    SecurityFrameworkConfig,
    SecurityFrameworkVersions,
    SecurityStagedChoice,
} from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
import { ProgrammingExerciseSecurityComponent } from 'app/programming/manage/update/update-components/security/programming-exercise-security.component';

const VERSIONS: SecurityFrameworkVersions = {
    selectableVersions: ['3.4.1', '3.3.0', '3.2.2'],
    templateVersions: { [ProjectType.PLAIN_GRADLE]: '3.2.2', [ProjectType.PLAIN_MAVEN]: '3.3.0' },
};

const inactiveConfig = (): SecurityFrameworkConfig => ({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '3.4.1' });
const activeConfig = (version = '3.4.1', hash = 'abc1234'): SecurityFrameworkConfig => ({
    status: SecurityActivationStatus.ACTIVE,
    frameworkVersion: version,
    lastCommitHash: hash,
    lastSyncedAt: '2026-09-15T10:00:00Z',
});

/**
 * Logic-focused spec: the component's status machine and its single server contract (getConfig /
 * activate / deactivate / updateFrameworkVersion), driven through a fully mocked
 * {@link SecurityFrameworkService}. The template is overridden to empty so the transitions are asserted
 * on the component's own signals, independent of the design-system child components.
 */
describe('ProgrammingExerciseSecurityComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseSecurityComponent>;
    let comp: ProgrammingExerciseSecurityComponent;
    let service: {
        getVersions: ReturnType<typeof vi.fn>;
        getConfig: ReturnType<typeof vi.fn>;
        activate: ReturnType<typeof vi.fn>;
        deactivate: ReturnType<typeof vi.fn>;
        updateFrameworkVersion: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        service = {
            getVersions: vi.fn().mockReturnValue(of(VERSIONS)),
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
        exercise.projectType = ProjectType.PLAIN_GRADLE;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('selectedProgrammingLanguage', language);
        fixture.detectChanges();
    }

    function initEdit(initial: SecurityFrameworkConfig, language = ProgrammingLanguage.JAVA): void {
        service.getConfig.mockReturnValue(of(initial));
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 42;
        exercise.programmingLanguage = language;
        exercise.projectType = ProjectType.PLAIN_GRADLE;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('selectedProgrammingLanguage', language);
        fixture.detectChanges();
    }

    it('shows the card only for supported languages (Java)', () => {
        initCreate(ProgrammingLanguage.JAVA);
        expect(comp.isSupportedLanguage()).toBe(true);
    });

    it('hides the card for the black-box project type, which runs its tests without Ares', () => {
        initCreate(ProgrammingLanguage.JAVA);
        fixture.componentRef.setInput('selectedProjectType', ProjectType.MAVEN_BLACKBOX);
        fixture.detectChanges();
        expect(comp.isAvailable()).toBe(false);

        fixture.componentRef.setInput('selectedProjectType', ProjectType.PLAIN_GRADLE);
        fixture.detectChanges();
        expect(comp.isAvailable()).toBe(true);
    });

    it('does not load the config while the card is hidden, and loads it once the card becomes available', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 42;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.projectType = ProjectType.MAVEN_BLACKBOX;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('selectedProgrammingLanguage', ProgrammingLanguage.JAVA);
        fixture.componentRef.setInput('selectedProjectType', ProjectType.MAVEN_BLACKBOX);
        fixture.detectChanges();
        expect(service.getConfig).not.toHaveBeenCalled();

        fixture.componentRef.setInput('selectedProjectType', ProjectType.PLAIN_GRADLE);
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
        initEdit(activeConfig('3.3.0', 'aaa1111'));
        expect(service.getConfig).toHaveBeenCalledWith(42);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.config().frameworkVersion).toBe('3.3.0');
    });

    it('create mode starts ACTIVE with the newest version, like the Java template', () => {
        initCreate();
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.isToggleOn()).toBe(true);
        expect(comp.config().frameworkVersion).toBe('3.2.2');
    });

    it('shows the version of the template in create mode, not the newest selectable one, so nothing differs from what gets saved', () => {
        initCreate();
        expect(comp.latestVersion()).toBe('3.4.1');
        expect(comp.config().frameworkVersion).toBe('3.2.2');
    });

    it('follows the template version of the selected project type until the instructor picks a version', () => {
        initCreate();
        fixture.componentRef.setInput('selectedProjectType', ProjectType.PLAIN_MAVEN);
        fixture.detectChanges();
        expect(comp.config().frameworkVersion).toBe('3.3.0');

        comp.onFrameworkVersionChanged('3.4.1');
        fixture.componentRef.setInput('stagedChoice', { active: true, frameworkVersion: '3.4.1' });
        fixture.componentRef.setInput('selectedProjectType', ProjectType.PLAIN_GRADLE);
        fixture.detectChanges();
        expect(comp.config().frameworkVersion).toBe('3.4.1');
    });

    it('ignores choosing a version that is not selectable', () => {
        initEdit(activeConfig('2.0.0'));
        comp.onFrameworkVersionChanged('2.0.0');
        expect(service.updateFrameworkVersion).not.toHaveBeenCalled();
    });

    it('activates an exercise that pins a no longer selectable version with the template version', () => {
        initEdit({ status: SecurityActivationStatus.INACTIVE, frameworkVersion: '2.0.0' });
        service.activate.mockReturnValue(of(activeConfig('3.2.2')));
        comp.onToggleChanged(true);
        expect(service.activate).toHaveBeenCalledWith(42, '3.2.2');
    });

    it('labels the newest version and keeps a no longer supported version the exercise pins', () => {
        initEdit(activeConfig('2.0.0'));
        expect(comp.frameworkVersions().map((option) => option.version)).toEqual(['3.4.1', '3.3.0', '3.2.2', '2.0.0']);
        expect(comp.frameworkVersions()[0].label).toContain('3.4.1 ');
        expect(comp.frameworkVersions()[1].label).toBe('3.3.0');
    });

    it('disables the version selector when no versions are known', () => {
        service.getVersions.mockReturnValue(throwError(() => new Error('LOAD_FAILED')));
        // The versions are requested on construction, so re-create the component with the failing request.
        fixture.destroy();
        fixture = TestBed.createComponent(ProgrammingExerciseSecurityComponent);
        comp = fixture.componentInstance;
        initCreate();
        expect(comp.isVersionSelectDisabled()).toBe(true);
    });

    it('create mode stages ACTIVE/INACTIVE locally without calling the server', () => {
        initCreate();
        comp.onToggleChanged(false);
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        comp.onToggleChanged(true);
        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
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

    it('blocks the toggle and version selector while the edit-mode config is loading', () => {
        const load$ = new Subject<SecurityFrameworkConfig>();
        service.getConfig.mockReturnValue(load$.asObservable());
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 42;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        fixture.componentRef.setInput('programmingExercise', exercise);
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
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 42;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        fixture.componentRef.setInput('programmingExercise', exercise);
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

    it('emits the staged choice to the parent when changed in create mode', () => {
        initCreate();
        const emitted: SecurityStagedChoice[] = [];
        comp.stagedChoiceChange.subscribe((value) => emitted.push(value));
        comp.onToggleChanged(false);
        expect(emitted.at(-1)).toEqual({ active: false, frameworkVersion: '3.2.2' });
        comp.onToggleChanged(true);
        expect(emitted.at(-1)).toEqual({ active: true, frameworkVersion: '3.2.2' });
        comp.onFrameworkVersionChanged('3.3.0');
        expect(emitted.at(-1)).toEqual({ active: true, frameworkVersion: '3.3.0' });
    });

    it('re-seeds a staged choice provided by the parent when the card is re-created', () => {
        fixture.componentRef.setInput('stagedChoice', { active: false, frameworkVersion: '3.3.0' });
        initCreate();
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);
        expect(comp.config().frameworkVersion).toBe('3.3.0');
    });

    it('returns to the template default when the parent clears the staged choice on a non-Java switch', () => {
        initCreate();
        comp.onToggleChanged(false);
        // The parent mirrors the emitted choice back into the input.
        fixture.componentRef.setInput('stagedChoice', { active: false, frameworkVersion: '3.2.2' });
        fixture.detectChanges();
        expect(comp.status()).toBe(SecurityActivationStatus.INACTIVE);

        // Switching to a non-Java language clears the choice in the parent; the card must not keep a
        // stale INACTIVE that would never be applied when the language returns to Java.
        fixture.componentRef.setInput('stagedChoice', undefined);
        fixture.detectChanges();

        expect(comp.status()).toBe(SecurityActivationStatus.ACTIVE);
        expect(comp.isToggleOn()).toBe(true);
        expect(comp.config().frameworkVersion).toBe('3.2.2');
    });
});
