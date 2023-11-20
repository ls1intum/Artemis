import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { BuildAction, ProgrammingExercise, ProgrammingLanguage, ProjectType, ScriptAction, WindFile } from 'app/entities/programming-exercise.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-custom-build-plan.component';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ElementRef, NgZone } from '@angular/core';
import { ThemeService } from 'app/core/theme/theme.service';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

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

    beforeEach(() => {
        const course = { id: 123 } as Course;
        programmingExercise = new ProgrammingExercise(course, undefined);
        programmingExercise.customizeBuildPlanWithAeolus = true;
        windFile = new WindFile();
        actions = [];
        gradleBuildAction = new ScriptAction();
        gradleBuildAction.name = 'gradle';
        gradleBuildAction.script = './gradlew clean test';
        cleanBuildAction = new ScriptAction();
        cleanBuildAction.name = 'clean';
        cleanBuildAction.script = `chmod -R 777 .`;
        actions.push(gradleBuildAction);
        actions.push(cleanBuildAction);
        windFile.actions = actions;
        programmingExercise.windFile = windFile;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseCustomBuildPlanComponent, MockComponent(FaIconComponent), MockComponent(HelpIconComponent), MockComponent(AceEditorComponent)],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .compileComponents()
            .then(() => {
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
        expect(programmingExercise.windFile?.actions.length).toBe(1);
        comp.deleteAction('clean');
        expect(programmingExercise.windFile?.actions.length).toBe(0);
    });

    it('should add action', () => {
        comp.addAction('gradle clean');
        expect(programmingExercise.windFile?.actions.length).toBe(3);
    });

    it('should accept editor', () => {
        const elementRef: ElementRef = new ElementRef(document.createElement('div'));
        const zone: NgZone = new NgZone({});
        comp.editor = new AceEditorComponent(elementRef, zone, mockThemeService);
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

    it('should return true', () => {
        expect(comp.isScriptAction(gradleBuildAction)).toBeTrue();
    });

    it('should return false', () => {
        expect(comp.isScriptAction(new BuildAction())).toBeFalse();
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
        // make a spy on the editor
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
        comp.loadAeolusTemplate();
        expect(comp.programmingLanguage).toBe(programmingExercise.programmingLanguage);
        expect(comp.projectType).toBe(programmingExercise.projectType);
        expect(comp.sequentialTestRuns).toBe(programmingExercise.sequentialTestRuns);
        expect(comp.staticCodeAnalysisEnabled).toBe(programmingExercise.staticCodeAnalysisEnabled);
        expect(comp.testwiseCoverageEnabled).toBe(programmingExercise.testwiseCoverageEnabled);
    });
});
