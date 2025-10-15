import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ProgrammingExercise Docker Image', () => {
    let comp: ProgrammingExerciseBuildConfigurationComponent;
    let fixture: any;
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

        fixture.componentRef.setInput('dockerImage', 'testImage');
        fixture.componentRef.setInput('timeout', 10);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.componentRef.setInput('isAeolus', false);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should update build values', () => {
        expect(comp.dockerImage()).toBe('testImage');
        comp.dockerImageChange.subscribe((value) => expect(value).toBe('newImage'));
        comp.dockerImageChange.emit('newImage');

        expect(comp.timeout()).toBe(10);
        comp.timeoutChange.subscribe((value) => expect(value).toBe(20));
        comp.timeoutChange.emit(20);
    });

    it('should set profile values', () => {
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            buildTimeoutMin: undefined,
            buildTimeoutMax: undefined,
            buildTimeoutDefault: undefined,
            defaultContainerCpuCount: undefined,
            defaultContainerMemoryLimitInMB: undefined,
            defaultContainerMemorySwapLimitInMB: undefined,
        } as unknown as ProfileInfo);

        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(10);
        expect(comp.timeoutMaxValue).toBe(240);
        expect(comp.timeoutDefaultValue).toBe(120);
        expect(comp.cpuCount).toBeUndefined();
        expect(comp.memory).toBeUndefined();
        expect(comp.memorySwap).toBeUndefined();

        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            buildTimeoutMin: 0,
            buildTimeoutMax: 360,
            buildTimeoutDefault: 60,
            defaultContainerCpuCount: 1,
            defaultContainerMemoryLimitInMB: 1024,
            defaultContainerMemorySwapLimitInMB: 2048,
        } as unknown as ProfileInfo);
        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(0);
        expect(comp.timeoutMaxValue).toBe(360);
        expect(comp.timeoutDefaultValue).toBe(60);
        expect(comp.cpuCount).toBe(1);
        expect(comp.memory).toBe(1024);
        expect(comp.memorySwap).toBe(2048);

        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            buildTimeoutMin: 100,
            buildTimeoutMax: 20,
            buildTimeoutDefault: 10,
        } as unknown as ProfileInfo);

        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(100);
        expect(comp.timeoutMaxValue).toBe(240);
        expect(comp.timeoutDefaultValue).toBe(120);
    });

    it('should parse docker flags correctly', () => {
        comp.envVars = [['key', 'value']];
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{"key":"value"}}');

        // selecting a custom network stores it and serializes correctly
        comp.onNetworkChange('custom');
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{"key":"value"},"network":"custom"}');

        comp.removeEnvVar(0);
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

    it('should omit network when default is selected', () => {
        // set custom first, then switch back to default (undefined)
        comp.onNetworkChange('someNet');
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toContain('"network":"someNet"');

        comp.onNetworkChange(undefined);
        comp.envVars = [];
        comp.cpuCount = undefined;
        comp.memory = undefined;
        comp.memorySwap = undefined;
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{}}');
    });

    it('should parse existing docker flags', () => {
        programmingExercise.buildConfig!.dockerFlags = '{"env":{"key":"value"}, "network":"none"}';
        comp.ngOnInit();
        expect(comp.network()).toBe('none');
        expect(comp.envVars).toEqual([['key', 'value']]);
    });

    it('should show warning when network none is selected', fakeAsync(() => {
        programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
        comp.setIsLanguageSupported();
        comp.onNetworkChange('none');
        fixture.detectChanges();

        const warning = fixture.nativeElement.querySelector('.alert-warning');
        expect(warning).not.toBeNull();
    }));

    it('should show no warning when a network other than none is selected', fakeAsync(() => {
        programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
        comp.setIsLanguageSupported();
        comp.onNetworkChange('default');
        fixture.detectChanges();

        const warning = fixture.nativeElement.querySelector('.alert-warning');
        expect(warning).toBeNull();
    }));

    it('should set supported languages', () => {
        programmingExercise.programmingLanguage = ProgrammingLanguage.EMPTY;
        comp.setIsLanguageSupported();
        expect(comp.isLanguageSupported).toBeFalse();

        programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
        comp.setIsLanguageSupported();
        expect(comp.isLanguageSupported).toBeTrue();
    });
});
