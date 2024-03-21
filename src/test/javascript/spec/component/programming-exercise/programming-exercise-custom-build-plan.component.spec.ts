import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import {
    BuildAction,
    DockerConfiguration,
    PlatformAction,
    ProgrammingExercise,
    ProgrammingLanguage,
    ProjectType,
    ScriptAction,
    WindFile,
    WindMetadata,
} from 'app/entities/programming-exercise.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ElementRef, NgZone } from '@angular/core';
import { ThemeService } from 'app/core/theme/theme.service';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { programmingExerciseCreationConfigMock } from './update-components/programming-exercise-creation-config-mock';
import { AeolusService } from 'app/exercises/programming/shared/service/aeolus.service';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-build-plan.component';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { Observable } from 'rxjs';

describe('ProgrammingExercise Custom Build Plan', () => {
    let mockThemeService: ThemeService;
    let comp: ProgrammingExerciseCustomBuildPlanComponent;
    const course = { id: 123 } as Course;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
    let programmingExercise = new ProgrammingExercise(course, undefined);
    let windFile: WindFile = new WindFile();
    let actions: BuildAction[] = [];
    let gradleBuildAction: ScriptAction = new ScriptAction();
    let cleanBuildAction: ScriptAction = new ScriptAction();
    let platformAction: PlatformAction = new PlatformAction();
    let mockAeolusService: AeolusService;

    beforeEach(() => {
        programmingExercise = new ProgrammingExercise(course, undefined);
        programmingExercise.customizeBuildPlanWithAeolus = true;
        windFile = new WindFile();
        const metadata = new WindMetadata();
        metadata.docker = new DockerConfiguration();
        metadata.docker.image = 'testImage';
        windFile.metadata = metadata;
        actions = [];
        gradleBuildAction = new ScriptAction();
        gradleBuildAction.name = 'gradle';
        gradleBuildAction.script = './gradlew clean test';
        platformAction = new PlatformAction();
        platformAction.name = 'platform';
        platformAction.kind = 'junit';
        cleanBuildAction = new ScriptAction();
        cleanBuildAction.name = 'clean';
        cleanBuildAction.script = `chmod -R 777 .`;
        actions.push(gradleBuildAction);
        actions.push(cleanBuildAction);
        actions.push(platformAction);
        windFile.actions = actions;
        programmingExercise.windFile = windFile;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseCustomBuildPlanComponent, MockComponent(FaIconComponent), MockComponent(HelpIconComponent), MockComponent(AceEditorComponent)],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .compileComponents()
            .then(() => {
                mockAeolusService = TestBed.inject(AeolusService);
                mockThemeService = TestBed.inject(ThemeService);
            });

        const fixture = TestBed.createComponent(ProgrammingExerciseCustomBuildPlanComponent);
        comp = fixture.componentInstance;

        comp.programmingExercise = programmingExercise;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set correct code', () => {
        programmingExercise.buildScript = 'nottest';
        comp.code = 'nottest';
        comp.codeChanged('test');
        expect(comp.code).toEqual(programmingExercise.buildScript);
        expect(comp.code).toBe('test');
    });

    it('should accept editor', () => {
        const elementRef: ElementRef = new ElementRef(document.createElement('div'));
        const zone: NgZone = new NgZone({});
        expect(comp.editor).toBeUndefined();
        comp.editor = new AceEditorComponent(elementRef, zone, mockThemeService);
        expect(comp.editor).toBeDefined();
    });

    it('should not fail if setting up undefined editor', () => {
        comp.setupEditor();
    });

    it('should return false to reload template', () => {
        comp.programmingLanguage = programmingExercise.programmingLanguage;
        comp.projectType = programmingExercise.projectType;
        comp.sequentialTestRuns = programmingExercise.sequentialTestRuns;
        comp.staticCodeAnalysisEnabled = programmingExercise.staticCodeAnalysisEnabled;
        comp.testwiseCoverageEnabled = programmingExercise.testwiseCoverageEnabled;
        expect(comp.shouldReloadTemplate()).toBeFalse();
    });

    it('should return true to reload template', () => {
        comp.programmingLanguage = ProgrammingLanguage.JAVA;
        comp.projectType = ProjectType.PLAIN_GRADLE;
        comp.sequentialTestRuns = programmingExercise.sequentialTestRuns;
        comp.staticCodeAnalysisEnabled = true;
        comp.testwiseCoverageEnabled = true;
        expect(comp.shouldReloadTemplate()).toBeTrue();
    });

    it('should reset buildplan', () => {
        programmingExercise.windFile = windFile;
        programmingExercise.buildPlanConfiguration = 'some build plan';
        expect(programmingExercise.windFile).toBeDefined();
        expect(programmingExercise.buildPlanConfiguration).toBeDefined();
        comp.resetCustomBuildPlan();
        expect(programmingExercise.windFile).toBeUndefined();
        expect(programmingExercise.buildPlanConfiguration).toBeUndefined();
    });

    it('should do nothing without a programming language', () => {
        comp.programmingLanguage = ProgrammingLanguage.JAVA;
        programmingExercise.programmingLanguage = undefined;
        comp.loadAeolusTemplate();
        expect(comp.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
    });

    it('should update component properties', () => {
        comp.programmingLanguage = undefined;
        comp.projectType = undefined;
        comp.sequentialTestRuns = undefined;
        comp.staticCodeAnalysisEnabled = undefined;
        comp.testwiseCoverageEnabled = undefined;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExerciseCreationConfig.customBuildPlansSupported = PROFILE_LOCALCI;
        comp.loadAeolusTemplate();
        expect(comp.programmingLanguage).toBe(programmingExercise.programmingLanguage);
        expect(comp.projectType).toBe(programmingExercise.projectType);
        expect(comp.sequentialTestRuns).toBe(programmingExercise.sequentialTestRuns);
        expect(comp.staticCodeAnalysisEnabled).toBe(programmingExercise.staticCodeAnalysisEnabled);
        expect(comp.testwiseCoverageEnabled).toBe(programmingExercise.testwiseCoverageEnabled);
    });

    it('should not call loadAeolusTemplate', () => {
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExerciseCreationConfig.customBuildPlansSupported = '';
        const loadAeolusTemplateSpy = jest.spyOn(comp, 'loadAeolusTemplate');
        comp.ngOnChanges({});
        expect(loadAeolusTemplateSpy).not.toHaveBeenCalled();
    });

    it('should call loadAeolusTemplate', () => {
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExerciseCreationConfig.customBuildPlansSupported = '';
        const loadAeolusTemplateSpy = jest.spyOn(comp, 'loadAeolusTemplate');
        comp.ngOnChanges({
            programmingExercise: {
                currentValue: programmingExercise,
                previousValue: undefined,
                firstChange: false,
                isFirstChange: function (): boolean {
                    throw new Error('Function not implemented.');
                },
            },
        });
        expect(loadAeolusTemplateSpy).toHaveBeenCalled();
    });

    it('should update windfile', () => {
        comp.programmingExercise.windFile = undefined;
        programmingExerciseCreationConfigMock.customBuildPlansSupported = PROFILE_LOCALCI;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.next(mockAeolusService.serializeWindFile(windFile))));
        jest.spyOn(mockAeolusService, 'getAeolusTemplateScript').mockReturnValue(new Observable((subscriber) => subscriber.next("echo 'test'")));
        comp.loadAeolusTemplate();
        expect(comp.programmingExercise.windFile).toBeDefined();
    });

    it('should call this.resetCustomBuildPlan', () => {
        comp.programmingExercise.windFile = undefined;
        programmingExerciseCreationConfigMock.customBuildPlansSupported = PROFILE_LOCALCI;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        const resetSpy = jest.spyOn(comp, 'resetCustomBuildPlan');
        jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.error('error')));
        comp.loadAeolusTemplate();
        expect(comp.programmingExercise.windFile).toBeUndefined();
        expect(resetSpy).toHaveBeenCalled();
    });

    it('should call getAeolusTemplateScript', () => {
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExerciseCreationConfig.customBuildPlansSupported = PROFILE_LOCALCI;
        comp.programmingExercise.id = 1;
        jest.spyOn(comp, 'resetCustomBuildPlan');
        jest.spyOn(mockAeolusService, 'getAeolusTemplateScript').mockReturnValue(new Observable((subscriber) => subscriber.next("echo 'test'")));
        jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.next(mockAeolusService.serializeWindFile(windFile))));
        comp.loadAeolusTemplate();
        expect(comp.resetCustomBuildPlan).not.toHaveBeenCalled();
        expect(comp.programmingExercise.buildScript).toBe("echo 'test'");
    });

    it('should call getAeolusTemplateScript and reset', () => {
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExerciseCreationConfig.customBuildPlansSupported = PROFILE_LOCALCI;
        comp.programmingExercise.id = 1;
        jest.spyOn(mockAeolusService, 'getAeolusTemplateScript').mockReturnValue(new Observable((subscriber) => subscriber.error('error')));
        jest.spyOn(comp, 'resetCustomBuildPlan');
        comp.loadAeolusTemplate();
        expect(comp.resetCustomBuildPlan).toHaveBeenCalledOnce();
        expect(comp.programmingExercise.buildScript).toBeUndefined();
    });

    it('should accept editor for existing exercise', () => {
        comp.programmingExercise.id = 1;
        const elementRef: ElementRef = new ElementRef(document.createElement('div'));
        const zone: NgZone = new NgZone({});
        comp.programmingExercise.buildScript = 'buildscript';
        const editor = new AceEditorComponent(elementRef, zone, mockThemeService);
        expect(comp.editor).toBeUndefined();
        comp.editor = editor;
        expect(comp.code).toBe('buildscript');
        expect(comp.editor).toBeDefined();
        comp.programmingExercise.buildScript = undefined;
        comp.editor = new AceEditorComponent(elementRef, zone, mockThemeService);
        expect(comp.code).toBe('');
    });

    it('should set docker image correctly', () => {
        comp.programmingExercise.windFile = windFile;
        comp.programmingExercise.windFile.metadata.docker.image = 'old';
        comp.setDockerImage('testImage');
        expect(comp.programmingExercise.windFile?.metadata.docker.image).toBe('testImage');
        comp.programmingExercise.windFile = undefined;
        comp.setDockerImage('testImage');
        expect(comp.programmingExercise.windFile).toBeUndefined();
    });
});
