import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';
import { ProgrammingExercise, ProgrammingLanguage } from '../../../../../main/webapp/app/entities/programming/programming-exercise.model';
import { Course } from '../../../../../main/webapp/app/entities/course.model';
import { ProgrammingExerciseBuildConfig } from '../../../../../main/webapp/app/entities/programming/programming-exercise-build.config';

describe('ProgrammingExercise Docker Image', () => {
    let comp: ProgrammingExerciseBuildConfigurationComponent;
    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.buildConfig = new ProgrammingExerciseBuildConfig();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, ArtemisProgrammingExerciseUpdateModule],
            declarations: [ProgrammingExerciseBuildConfigurationComponent],
            providers: [],
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

    it('should update network flag value', () => {
        comp.onDisableNetworkAccessChange({ target: { checked: true } });
        expect(comp.isNetworkDisabled).toBeTrue();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('[["network","none"]]');

        comp.onDisableNetworkAccessChange({ target: { checked: false } });
        expect(comp.isNetworkDisabled).toBeFalse();
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('[]');
    });

    it('should update env vars', () => {
        comp.onEnvVarsChange('\'key\'="value"');
        expect(comp.envVars).toBe('\'key\'="value"');
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('[["env","\'key\'=\\"value\\""]]');

        comp.onEnvVarsChange('');
        expect(comp.envVars).toBe('');
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('[]');
    });

    it('should handle both network and env vars', () => {
        comp.onDisableNetworkAccessChange({ target: { checked: true } });
        comp.onEnvVarsChange('key=value');
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('[["network","none"],["env","key=value"]]');

        comp.onDisableNetworkAccessChange({ target: { checked: false } });
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('[["env","key=value"]]');

        comp.onDisableNetworkAccessChange({ target: { checked: true } });
        comp.onEnvVarsChange('');
        expect(comp.programmingExercise()?.buildConfig?.dockerFlags).toBe('[["network","none"]]');
    });

    it('should parse existing docker flags', () => {
        programmingExercise.buildConfig!.dockerFlags = '[["network","none"],["env","key=value"]]';
        comp.ngOnInit();
        expect(comp.isNetworkDisabled).toBeTrue();
        expect(comp.envVars).toBe('key=value');
    });

    it('should set supported languages', () => {
        programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
        comp.setIsLanguageSupported();
        expect(comp.isLanguageSupported).toBeFalse();

        programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        comp.setIsLanguageSupported();
        expect(comp.isLanguageSupported).toBeTrue();
    });
});
