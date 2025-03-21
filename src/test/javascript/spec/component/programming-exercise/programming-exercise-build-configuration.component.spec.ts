import { TestBed } from '@angular/core/testing';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ProgrammingExerciseBuildConfig } from 'app/entities/programming/programming-exercise-build.config';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExercise Docker Image', () => {
    let comp: ProgrammingExerciseBuildConfigurationComponent;
    let profileServiceMock: { getProfileInfo: jest.Mock };
    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.buildConfig = new ProgrammingExerciseBuildConfig();

    beforeEach(() => {
        profileServiceMock = {
            getProfileInfo: jest.fn(),
        };

        TestBed.configureTestingModule({
            imports: [FormsModule],
            providers: [
                { provide: ProfileService, useValue: profileServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then();

        const fixture = TestBed.createComponent(ProgrammingExerciseBuildConfigurationComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('dockerImage', 'testImage');
        fixture.componentRef.setInput('timeout', 10);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
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
        profileServiceMock.getProfileInfo.mockReturnValue(
            of({
                buildTimeoutMin: undefined,
                buildTimeoutMax: undefined,
                buildTimeoutDefault: undefined,
                defaultContainerCpuCount: undefined,
                defaultContainerMemoryLimitInMB: undefined,
                defaultContainerMemorySwapLimitInMB: undefined,
            }),
        );

        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(10);
        expect(comp.timeoutMaxValue).toBe(240);
        expect(comp.timeoutDefaultValue).toBe(120);
        expect(comp.cpuCount).toBeUndefined();
        expect(comp.memory).toBeUndefined();
        expect(comp.memorySwap).toBeUndefined();

        profileServiceMock.getProfileInfo.mockReturnValue(
            of({
                buildTimeoutMin: 0,
                buildTimeoutMax: 360,
                buildTimeoutDefault: 60,
                defaultContainerCpuCount: 1,
                defaultContainerMemoryLimitInMB: 1024,
                defaultContainerMemorySwapLimitInMB: 2048,
            }),
        );
        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(0);
        expect(comp.timeoutMaxValue).toBe(360);
        expect(comp.timeoutDefaultValue).toBe(60);
        expect(comp.cpuCount).toBe(1);
        expect(comp.memory).toBe(1024);
        expect(comp.memorySwap).toBe(2048);

        profileServiceMock.getProfileInfo.mockReturnValue(
            of({
                buildTimeoutMin: 100,
                buildTimeoutMax: 20,
                buildTimeoutDefault: 10,
            }),
        );

        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(100);
        expect(comp.timeoutMaxValue).toBe(240);
        expect(comp.timeoutDefaultValue).toBe(120);
    });

    it('should set custom network flag value', () => {
        comp.onUseCustomNetworkToggle({ target: { checked: true } });
        expect(comp.useCustomNetwork()).toBeTrue();

        comp.onUseCustomNetworkToggle({ target: { checked: false } });
        expect(comp.useCustomNetwork()).toBeFalse();
    });

    it('should remove selected network when flag is un-checked', () => {
        comp.network.set('some_network');
        comp.onUseCustomNetworkToggle({ target: { checked: false } });

        expect(comp.network()).toBeUndefined();
    });

    it('should parse docker flags correctly', () => {
        comp.envVars = [['key', 'value']];
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{"key":"value"}}');

        comp.network.set('custom');
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

    it('should parse existing docker flags', () => {
        profileServiceMock.getProfileInfo.mockReturnValue(
            of({
                buildTimeoutMin: undefined,
                buildTimeoutMax: undefined,
                buildTimeoutDefault: undefined,
            }),
        );

        programmingExercise.buildConfig!.dockerFlags = '{"env":{"key":"value"}, "network":"none"}';
        comp.ngOnInit();
        expect(comp.network()).toBe('none');
        expect(comp.envVars).toEqual([['key', 'value']]);
    });

    it('should set supported languages', () => {
        programmingExercise.programmingLanguage = ProgrammingLanguage.EMPTY;
        comp.setIsLanguageSupported();
        expect(comp.isLanguageSupported).toBeFalse();

        programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
        comp.setIsLanguageSupported();
        expect(comp.isLanguageSupported).toBeTrue();
    });
});
