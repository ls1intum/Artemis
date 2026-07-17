import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/build-plan-editor/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Course } from 'app/course/shared/entities/course.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DOCKER_FLAGS_MAX_LENGTH } from 'app/programming/shared/entities/programming-exercise-build.config';
import { By } from '@angular/platform-browser';

describe('ProgrammingExercise Docker Image', () => {
    let comp: ProgrammingExerciseBuildConfigurationComponent;
    let fixture: ComponentFixture<ProgrammingExerciseBuildConfigurationComponent>;
    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.buildConfig = new ProgrammingExerciseBuildConfig();
    let profileService: ProfileService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(ProgrammingExerciseBuildConfigurationComponent);
        comp = fixture.componentInstance;

        profileService = TestBed.inject(ProfileService);

        fixture.componentRef.setInput('timeout', 10);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        // the exercise is shared by every test in this suite and parseDockerFlagsToString writes into it, so a test that
        // edits the flags would otherwise leak them into the next one through ngOnInit
        programmingExercise.buildConfig!.dockerFlags = undefined;
    });

    it('should update build values', () => {
        expect(comp.timeout()).toBe(10);
        comp.timeoutChange.subscribe((value) => expect(value).toBe(20));
        comp.timeoutChange.emit(20);
    });

    it('should set profile values', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            buildTimeoutMin: undefined,
            buildTimeoutMax: undefined,
            buildTimeoutDefault: undefined,
            defaultContainerCpuCount: undefined,
            defaultContainerMemoryLimitInMB: undefined,
            defaultContainerMemorySwapLimitInMB: undefined,
        } as unknown as ProfileInfo);

        comp.ngOnInit();
        expect(comp.timeoutMinValue()).toBe(10);
        expect(comp.timeoutMaxValue()).toBe(240);
        expect(comp.timeoutDefaultValue()).toBe(120);
        expect(comp.cpuCount()).toBeUndefined();
        expect(comp.memory()).toBeUndefined();
        expect(comp.memorySwap()).toBeUndefined();

        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            buildTimeoutMin: 0,
            buildTimeoutMax: 360,
            buildTimeoutDefault: 60,
            defaultContainerCpuCount: 1,
            defaultContainerMemoryLimitInMB: 1024,
            defaultContainerMemorySwapLimitInMB: 2048,
        } as unknown as ProfileInfo);
        comp.ngOnInit();
        expect(comp.timeoutMinValue()).toBe(0);
        expect(comp.timeoutMaxValue()).toBe(360);
        expect(comp.timeoutDefaultValue()).toBe(60);
        expect(comp.cpuCount()).toBe(1);
        expect(comp.memory()).toBe(1024);
        expect(comp.memorySwap()).toBe(2048);

        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            buildTimeoutMin: 100,
            buildTimeoutMax: 20,
            buildTimeoutDefault: 10,
        } as unknown as ProfileInfo);

        comp.ngOnInit();
        expect(comp.timeoutMinValue()).toBe(100);
        expect(comp.timeoutMaxValue()).toBe(240);
        expect(comp.timeoutDefaultValue()).toBe(120);
    });

    it('should not emit the default timeout when the stored timeout is 0', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            buildTimeoutMin: undefined,
            buildTimeoutMax: undefined,
            buildTimeoutDefault: undefined,
        } as unknown as ProfileInfo);
        fixture.componentRef.setInput('timeout', 0);
        const emitStub = vi.spyOn(comp.timeoutChange, 'emit');

        comp.ngOnInit();

        // emitting the default here would pin it on the next save, so the model must stay at the 0 "use default" sentinel
        expect(emitStub).not.toHaveBeenCalled();
        expect(comp.usesDefaultTimeout()).toBe(true);
        // the slider still renders at the default position even though the bound value is 0
        expect(comp.displayTimeout()).toBe(120);
    });

    it('should display the concrete timeout when one is set', () => {
        fixture.componentRef.setInput('timeout', 30);
        comp.ngOnInit();

        expect(comp.usesDefaultTimeout()).toBe(false);
        expect(comp.displayTimeout()).toBe(30);
    });

    it('should parse docker flags correctly', () => {
        comp.envVars.set([['key', 'value']]);
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{"key":"value"}}');

        // selecting a custom network stores it and serializes correctly
        comp.onNetworkChange('custom');
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{"key":"value"},"network":"custom"}');

        comp.removeEnvVar(comp.envVars()[0]);
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{},"network":"custom"}');

        comp.addEnvVar();
        const mockEventMemory = { target: { value: 1024 } };
        const mockEventCpu = { target: { value: 1 } };
        const mockEventMemorySwap = { target: { value: 2048 } };
        comp.onMemoryChange(mockEventMemory);
        comp.onCpuCountChange(mockEventCpu);
        comp.onMemorySwapChange(mockEventMemorySwap);
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{},"network":"custom","cpuCount":1,"memory":1024,"memorySwap":2048}');
    });

    it('should coerce numeric resource limits entered as text into numbers', () => {
        // the fields are free text, so without coercion the flags would carry strings and the save would fail server-side
        comp.onCpuCountChange({ target: { value: '2' } });
        comp.onMemoryChange({ target: { value: ' 1024 ' } });
        comp.onMemorySwapChange({ target: { value: '0' } });

        expect(comp.cpuCount()).toBe(2);
        expect(comp.memory()).toBe(1024);
        expect(comp.memorySwap()).toBe(0);
        expect(comp.areDockerResourcesValid()).toBe(true);
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toContain('"cpuCount":2');
    });

    const invalidResourceLimits: [string, string][] = [
        ['a non-numeric value', 'abc'],
        ['an empty value', ''],
        ['a fractional value', '1.5'],
    ];

    it.each(invalidResourceLimits)('should flag %s for the cpu count and keep the last valid value', (_description, invalidValue) => {
        comp.onCpuCountChange({ target: { value: '4' } });
        const flagsAfterValidValue = comp.programmingExercise()?.buildConfig?.dockerFlags;

        comp.onCpuCountChange({ target: { value: invalidValue } });

        expect(comp.isCpuCountValid()).toBe(false);
        expect(comp.areDockerResourcesValid()).toBe(false);
        // the invalid input must not reach the docker flags, so the last valid configuration stays intact
        expect(comp.cpuCount()).toBe(4);
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe(flagsAfterValidValue);
    });

    it('should reject resource limits below the server minimum', () => {
        comp.onCpuCountChange({ target: { value: '0' } });
        comp.onMemoryChange({ target: { value: '5' } });
        comp.onMemorySwapChange({ target: { value: '-1' } });

        // these mirror the bounds of ProgrammingExerciseValidationService#validateDockerFlags
        expect(comp.isCpuCountValid()).toBe(false);
        expect(comp.isMemoryValid()).toBe(false);
        expect(comp.isMemorySwapValid()).toBe(false);
    });

    it('should accept resource limits exactly at the server minimum', () => {
        comp.onCpuCountChange({ target: { value: '1' } });
        comp.onMemoryChange({ target: { value: '6' } });
        comp.onMemorySwapChange({ target: { value: '0' } });

        expect(comp.areDockerResourcesValid()).toBe(true);
    });

    it('should flag docker flags that are already too long when the exercise is opened', () => {
        // an exercise saved before the limit existed can carry oversized flags, so the check has to hold on load as well
        programmingExercise.buildConfig!.dockerFlags = JSON.stringify({ env: { key: 'a'.repeat(DOCKER_FLAGS_MAX_LENGTH) } });

        fixture.detectChanges();

        expect(comp.areDockerFlagsWithinSizeLimit()).toBe(false);
    });

    it('should flag docker flags that grow too long through the editable field, not only through a direct signal write', () => {
        // the row is edited in place, so the size check only sees the new value if the handler republishes the signal.
        // Setting envVars directly would notify on its own and hide that, so this drives the handler the table calls.
        comp.envVars.set([['key', 'value']]);
        expect(comp.areDockerFlagsWithinSizeLimit()).toBe(true);

        comp.onEnvVarsValueChange(comp.envVars()[0])('a'.repeat(DOCKER_FLAGS_MAX_LENGTH));

        expect(comp.areDockerFlagsWithinSizeLimit()).toBe(false);
    });

    it('should render the docker flags size message next to the environment variables', () => {
        // the first detectChanges runs ngOnInit, which repopulates envVars from the exercise, so seed the rows after it
        fixture.detectChanges();
        comp.envVars.set([['key', 'value']]);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[jhiTranslate$="dockerFlagsTooLong"]'))).toBeNull();

        comp.onEnvVarsValueChange(comp.envVars()[0])('a'.repeat(DOCKER_FLAGS_MAX_LENGTH));
        fixture.detectChanges();

        const message = fixture.debugElement.query(By.css('[jhiTranslate$="dockerFlagsTooLong"]'));
        expect(message).not.toBeNull();
        // it has to sit in the same block as the environment variables, not at the bottom of the page
        expect(message.nativeElement.parentElement?.querySelector('#envVarsTable')).not.toBeNull();
    });

    it('should publish a new envVars array when a row is edited, so derived state is not left stale', () => {
        comp.envVars.set([['key', 'value']]);
        const before = comp.envVars();

        comp.onEnvVarsValueChange(before[0])('edited');

        // the row is mutated in place, so without republishing the array the signal would notify nothing
        expect(comp.envVars()).not.toBe(before);
        expect(comp.envVars()[0][1]).toBe('edited');
    });

    it('should keep removing a row working after it was edited in place', () => {
        comp.envVars.set([['key', 'value']]);
        const row = comp.envVars()[0];
        comp.onEnvVarsValueChange(row)('edited');

        // removeEnvVar identifies the row by reference, so republishing the signal must not replace the row object
        comp.removeEnvVar(comp.envVars()[0]);

        expect(comp.envVars()).toEqual([]);
    });

    it('should accept docker flags that stay within the maximum length', () => {
        programmingExercise.buildConfig!.dockerFlags = JSON.stringify({ env: { key: 'value' } });

        fixture.detectChanges();

        expect(comp.areDockerFlagsWithinSizeLimit()).toBe(true);
    });

    it('should update environment variable rows with new array references when adding and removing rows', () => {
        comp.envVars.set([]);

        const envVarsBeforeAdd = comp.envVars();
        comp.addEnvVar();

        expect(comp.envVars()).not.toBe(envVarsBeforeAdd);
        expect(comp.envVars()).toEqual([['', '']]);

        const envVarsBeforeRemove = comp.envVars();
        comp.removeEnvVar(comp.envVars()[0]);

        expect(comp.envVars()).not.toBe(envVarsBeforeRemove);
        expect(comp.envVars()).toEqual([]);
    });

    it('should omit network when default is selected', () => {
        // set custom first, then switch back to default (undefined)
        comp.onNetworkChange('someNet');
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toContain('"network":"someNet"');

        comp.onNetworkChange('');
        comp.envVars.set([]);
        comp.cpuCount.set(undefined);
        comp.memory.set(undefined);
        comp.memorySwap.set(undefined);
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{}}');
    });

    it('should parse existing docker flags', () => {
        programmingExercise.buildConfig!.dockerFlags = '{"env":{"key":"value"}, "network":"none"}';
        comp.ngOnInit();
        expect(comp.network()).toBe('none');
        expect(comp.envVars()).toEqual([['key', 'value']]);
    });

    it('should show warning when network none is selected', () => {
        programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
        comp.setIsLanguageSupported();
        comp.onNetworkChange('none');
        fixture.detectChanges();

        const warning = fixture.nativeElement.querySelector('.alert-warning');
        expect(warning).not.toBeNull();
    });

    it('should show no warning when a network other than none is selected', () => {
        programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
        comp.setIsLanguageSupported();
        comp.onNetworkChange('default');
        fixture.detectChanges();

        const warning = fixture.nativeElement.querySelector('.alert-warning');
        expect(warning).toBeNull();
    });

    it('should set supported languages', () => {
        programmingExercise.programmingLanguage = ProgrammingLanguage.EMPTY;
        comp.setIsLanguageSupported();
        expect(comp.isLanguageSupported()).toBe(false);

        programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
        comp.setIsLanguageSupported();
        expect(comp.isLanguageSupported()).toBe(true);
    });
});
