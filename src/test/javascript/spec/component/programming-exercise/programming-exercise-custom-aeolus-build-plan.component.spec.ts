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
import { ProgrammingExerciseCustomAeolusBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-aeolus-build-plan.component';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ElementRef, NgZone } from '@angular/core';
import { ThemeService } from 'app/core/theme/theme.service';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { programmingExerciseCreationConfigMock } from './update-components/programming-exercise-creation-config-mock';
import { AeolusService } from 'app/exercises/programming/shared/service/aeolus.service';
import { PROFILE_AEOLUS } from 'app/app.constants';
import { Observable } from 'rxjs';

describe('ProgrammingExercise Aeolus Custom Build Plan', () => {
    let mockThemeService: ThemeService;
    let comp: ProgrammingExerciseCustomAeolusBuildPlanComponent;
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
        cleanBuildAction.parameters = new Map<string, string | boolean | number>();
        cleanBuildAction.parameters.set('testparam', 'testkey');
        actions.push(gradleBuildAction);
        actions.push(cleanBuildAction);
        actions.push(platformAction);
        windFile.actions = actions;
        programmingExercise.windFile = windFile;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseCustomAeolusBuildPlanComponent, MockComponent(FaIconComponent), MockComponent(HelpIconComponent), MockComponent(AceEditorComponent)],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .compileComponents()
            .then(() => {
                mockAeolusService = TestBed.inject(AeolusService);
                mockThemeService = TestBed.inject(ThemeService);
            });

        const fixture = TestBed.createComponent(ProgrammingExerciseCustomAeolusBuildPlanComponent);
        comp = fixture.componentInstance;

        comp.programmingExercise = programmingExercise;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set correct code', () => {
        comp.changeActiveAction('gradle');
        expect(comp.code).toEqual(gradleBuildAction.script);
        expect(comp.active).toEqual(gradleBuildAction);
    });

    it('should change the code on active action change', () => {
        comp.changeActiveAction('gradle');
        expect(comp.code).toEqual(gradleBuildAction.script);
        expect(comp.active).toEqual(gradleBuildAction);
        comp.changeActiveAction('clean');
        expect(comp.code).toEqual(cleanBuildAction.script);
        expect(comp.active).toEqual(cleanBuildAction);
    });

    it('should delete action', () => {
        comp.deleteAction('gradle');
        const size = programmingExercise.windFile?.actions.length;
        expect(size).toBeDefined();
        const realSize = size!;
        expect(programmingExercise.windFile?.actions.length).toBe(realSize);
        comp.deleteAction('clean');
        expect(programmingExercise.windFile?.actions.length).toBe(realSize - 1);
    });

    it('should add action', () => {
        const size = programmingExercise.windFile?.actions.length;
        expect(size).toBeDefined();
        comp.addAction('gradle clean');
        expect(programmingExercise.windFile?.actions.length).toBe(size! + 1);
    });

    it('should accept editor', () => {
        const elementRef: ElementRef = new ElementRef(document.createElement('div'));
        const zone: NgZone = new NgZone({});
        expect(comp.editor).toBeUndefined();
        comp.editor = new AceEditorComponent(elementRef, zone, mockThemeService);
        expect(comp.editor).toBeDefined();
    });

    it('should change code of active action', () => {
        comp.changeActiveAction('gradle');
        expect(comp.code).toBe(gradleBuildAction.script);
        comp.codeChanged('test');
        expect(gradleBuildAction.script).toBe('test');
    });

    it('should change code if deleting active action and unset active action', () => {
        comp.changeActiveAction('gradle');
        expect(comp.code).toBe(gradleBuildAction.script);
        comp.deleteAction('gradle');
        expect(comp.code).toBe('');
        expect(comp.active).toBeUndefined();
    });

    it('should do nothing without a Windfile', () => {
        comp.programmingExercise.windFile = undefined;
        comp.code = 'this should not change';
        comp.changeActiveAction('');
        expect(comp.code).toBe('this should not change');
    });

    it('should do nothing on delete invalid action', () => {
        comp.changeActiveAction('');
        expect(comp.code).toBe('');
        expect(comp.active).toBeUndefined();
    });

    it('should not fail if setting up undefined editor', () => {
        comp.setupEditor();
        expect(comp.editor).toBeUndefined();
    });

    it('should change code', () => {
        comp.changeActiveAction('gradle');
        comp.codeChanged('this is some code');
        const action: BuildAction | undefined = comp.active;
        expect(action).toBeDefined();
        expect(action).toBeInstanceOf(ScriptAction);
        if (action instanceof ScriptAction) {
            expect(action.script).toBe('this is some code');
        }
    });

    it('should set editor text', () => {
        const elementRef: ElementRef = new ElementRef(document.createElement('div'));
        const zone: NgZone = new NgZone({});
        comp.editor = new AceEditorComponent(elementRef, zone, mockThemeService);
        comp.changeActiveAction('gradle');
        expect(comp.editor.text).toBe(gradleBuildAction.script);
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
        comp.programmingExerciseCreationConfig.customBuildPlansSupported = PROFILE_AEOLUS;
        comp.loadAeolusTemplate();
        expect(comp.programmingLanguage).toBe(programmingExercise.programmingLanguage);
        expect(comp.projectType).toBe(programmingExercise.projectType);
        expect(comp.sequentialTestRuns).toBe(programmingExercise.sequentialTestRuns);
        expect(comp.staticCodeAnalysisEnabled).toBe(programmingExercise.staticCodeAnalysisEnabled);
        expect(comp.testwiseCoverageEnabled).toBe(programmingExercise.testwiseCoverageEnabled);
    });

    it('should not call loadAeolusTemplate', () => {
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExerciseCreationConfig.customBuildPlansSupported = PROFILE_AEOLUS;
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
        programmingExerciseCreationConfigMock.customBuildPlansSupported = PROFILE_AEOLUS;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.next(mockAeolusService.serializeWindFile(windFile))));
        comp.loadAeolusTemplate();
        expect(comp.programmingExercise.windFile).toBeDefined();
        expect(comp.programmingExercise.windFile).toEqual(windFile);
    });

    it('should call this.resetCustomBuildPlan', () => {
        comp.programmingExercise.windFile = undefined;
        programmingExerciseCreationConfigMock.customBuildPlansSupported = PROFILE_AEOLUS;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        const resetSpy = jest.spyOn(comp, 'resetCustomBuildPlan');
        jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.error('error')));
        comp.loadAeolusTemplate();
        expect(comp.programmingExercise.windFile).toBeUndefined();
        expect(resetSpy).toHaveBeenCalled();
    });

    it('should parse windfile correctly', () => {
        const parsedWindFile = mockAeolusService.parseWindFile(mockAeolusService.serializeWindFile(windFile));
        expect(parsedWindFile).toBeDefined();
        expect(parsedWindFile?.actions.length).toBe(3);
        expect(parsedWindFile?.actions[0]).toBeInstanceOf(ScriptAction);
        expect(parsedWindFile?.actions[1]).toBeInstanceOf(ScriptAction);
        expect(parsedWindFile?.actions[2]).toBeInstanceOf(PlatformAction);
        expect(parsedWindFile?.actions[1].parameters).toBeDefined();
        expect(parsedWindFile?.actions[1].parameters).toEqual(cleanBuildAction.parameters);
    });

    it('should return undefined on invalid windfile', () => {
        const parsedWindFile = mockAeolusService.parseWindFile('{invalid json}');
        expect(parsedWindFile).toBeUndefined();
    });

    it('should add parameter to active action', () => {
        comp.active = gradleBuildAction;
        expect(comp.active?.parameters?.size).toBe(0);
        comp.addParameter();
        expect(comp.active?.parameters?.size).toBe(1);
    });

    it('should delete parameter of active action', () => {
        comp.active = cleanBuildAction;
        expect(comp.active?.parameters?.size).toBe(1);
        comp.deleteParameter('testparam');
        expect(comp.active?.parameters?.size).toBe(0);
    });

    it('should return parameter keys', () => {
        comp.active = cleanBuildAction;
        expect(comp.getParameterKeys()).toHaveLength(1);
        expect(comp.getParameterKeys()[0]).toBe('testparam');
    });

    it('should return empty string for non existing parameter', () => {
        comp.active = cleanBuildAction;
        expect(comp.getParameter('nonExisting')).toBe('');
        comp.active = undefined;
        expect(comp.getParameter('nonExisting')).toBe('');
    });

    it('should return empty array', () => {
        comp.active = undefined;
        expect(comp.getParameterKeys()).toHaveLength(0);
    });

    it('should not call loadAeolusTemplate on existing exercise', () => {
        comp.programmingExercise.id = 1;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        const resetCustomBuildPlanSpy = jest.spyOn(comp, 'resetCustomBuildPlan');
        comp.loadAeolusTemplate();
        expect(resetCustomBuildPlanSpy).not.toHaveBeenCalled();
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
