import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { ProfileService } from '../../../../../main/webapp/app/shared/layouts/profiles/profile.service';
import { ProgrammingExercise, ProgrammingLanguage } from '../../../../../main/webapp/app/entities/programming/programming-exercise.model';
import { Course } from '../../../../../main/webapp/app/entities/course.model';
import { ProgrammingExerciseBuildConfig } from '../../../../../main/webapp/app/entities/programming/programming-exercise-build.config';

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
            imports: [ArtemisTestModule, FormsModule],
            providers: [{ provide: ProfileService, useValue: profileServiceMock }],
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
                defaultContainerMemorySwapInMB: undefined,
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
                defaultContainerMemorySwapInMB: 2048,
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

    it('should update network flag value', () => {
        comp.onDisableNetworkAccessChange({ target: { checked: true } });
        expect(comp.isNetworkDisabled).toBeTrue();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"network":"none","env":{}}');

        comp.onDisableNetworkAccessChange({ target: { checked: false } });
        expect(comp.isNetworkDisabled).toBeFalse();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{}}');
    });

    it('should parse docker flags correctly', () => {
        comp.envVars = [['key', 'value']];
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"env":{"key":"value"}}');

        comp.isNetworkDisabled = true;
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"network":"none","env":{"key":"value"}}');

        comp.removeEnvVar(0);
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"network":"none","env":{}}');

        comp.addEnvVar();
        const mockEventMemory = { target: { value: 1024 } };
        const mockEventCpu = { target: { value: 1 } };
        const mockEventMemorySwap = { target: { value: 2048 } };
        comp.onMemoryChange(mockEventMemory);
        comp.onCpuCountChange(mockEventCpu);
        comp.onMemorySwapChange(mockEventMemorySwap);
        comp.parseDockerFlagsToString();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('{"network":"none","env":{},"cpuCount":1,"memory":1024,"memorySwap":2048}');
    });

    it('should parse existing docker flags', () => {
        profileServiceMock.getProfileInfo.mockReturnValue(
            of({
                buildTimeoutMin: undefined,
                buildTimeoutMax: undefined,
                buildTimeoutDefault: undefined,
            }),
        );

        programmingExercise.buildConfig!.dockerFlags = '{"network":"none","env":{"key":"value"}}';
        comp.ngOnInit();
        expect(comp.isNetworkDisabled).toBeTrue();
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
