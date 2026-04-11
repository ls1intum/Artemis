import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MockComponent, MockDirective } from 'ng-mocks';
import { Course } from 'app/core/course/shared/entities/course.model';
import { BuildPhasesEditorComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phases-editor.component';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-build-plan.component';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ProgrammingExerciseCustomBuildPlanComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseCustomBuildPlanComponent>;
    let comp: ProgrammingExerciseCustomBuildPlanComponent;
    let programmingExercise: ProgrammingExercise;
    let creationConfig: ProgrammingExerciseCreationConfig;

    const course = { id: 123 } as Course;
    const templatePhases: BuildPlanPhases = {
        phases: [{ name: 'build', script: './gradlew test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
        dockerImage: 'gradle:8',
    };

    const buildPhasesTemplateServiceMock = {
        getTemplate: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseCustomBuildPlanComponent],
            providers: [{ provide: BuildPhasesTemplateService, useValue: buildPhasesTemplateServiceMock }],
        })
            .overrideComponent(ProgrammingExerciseCustomBuildPlanComponent, {
                remove: { imports: [BuildPhasesEditorComponent, HelpIconComponent, ProgrammingExerciseBuildConfigurationComponent, TranslateDirective] },
                add: {
                    imports: [
                        MockComponent(BuildPhasesEditorComponent),
                        MockComponent(HelpIconComponent),
                        MockComponent(ProgrammingExerciseBuildConfigurationComponent),
                        MockDirective(TranslateDirective),
                    ],
                },
            })
            .compileComponents();

        programmingExercise = new ProgrammingExercise(course, undefined);
        programmingExercise.customizeBuildPlan = true;
        creationConfig = {
            buildPlanLoaded: false,
            isImportFromFile: false,
            customBuildPlansSupported: '',
        } as ProgrammingExerciseCreationConfig;

        fixture = TestBed.createComponent(ProgrammingExerciseCustomBuildPlanComponent);
        comp = fixture.componentInstance;
        comp.programmingExercise = programmingExercise;
        comp.programmingExerciseCreationConfig = creationConfig;
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should not reload template when config did not change', () => {
        comp.programmingLanguage = programmingExercise.programmingLanguage;
        comp.projectType = programmingExercise.projectType;
        comp.sequentialTestRuns = programmingExercise.buildConfig?.sequentialTestRuns;
        comp.staticCodeAnalysisEnabled = programmingExercise.staticCodeAnalysisEnabled;

        expect(comp.shouldReloadTemplate()).toBeFalse();
    });

    it('should reload template when config changed', () => {
        comp.programmingLanguage = ProgrammingLanguage.EMPTY;
        comp.projectType = ProjectType.PLAIN;
        comp.sequentialTestRuns = true;
        comp.staticCodeAnalysisEnabled = true;

        expect(comp.shouldReloadTemplate()).toBeTrue();
    });

    it('should reset custom build plan', () => {
        programmingExercise.buildConfig!.buildPlanConfiguration = 'x';
        programmingExercise.buildConfig!.buildScript = 'y';

        comp.resetCustomBuildPlan();

        expect(programmingExercise.buildConfig?.buildPlanConfiguration).toBeUndefined();
        expect(programmingExercise.buildConfig?.buildScript).toBeUndefined();
    });

    it('should do nothing when no programming language is set', () => {
        programmingExercise.programmingLanguage = undefined;

        comp.loadBuildPhasesTemplate();

        expect(buildPhasesTemplateServiceMock.getTemplate).not.toHaveBeenCalled();
    });

    it('should load build phases template and update component state', () => {
        buildPhasesTemplateServiceMock.getTemplate.mockReturnValue(of(templatePhases));

        comp.loadBuildPhasesTemplate();

        expect(buildPhasesTemplateServiceMock.getTemplate).toHaveBeenCalled();
        expect(comp.buildPlanPhases).toEqual(templatePhases);
        expect(comp.programmingExerciseCreationConfig.buildPlanLoaded).toBeTrue();
        expect(comp.programmingExercise.buildConfig?.timeoutSeconds).toBe(0);
    });

    it('should reset custom build plan when template loading fails', () => {
        programmingExercise.buildConfig!.buildPlanConfiguration = 'x';
        programmingExercise.buildConfig!.buildScript = 'y';
        buildPhasesTemplateServiceMock.getTemplate.mockReturnValue(throwError(() => new Error('error')));

        comp.loadBuildPhasesTemplate();

        expect(programmingExercise.buildConfig?.buildPlanConfiguration).toBeUndefined();
        expect(programmingExercise.buildConfig?.buildScript).toBeUndefined();
    });

    it('should not load template when import from file and build plan configuration exists', () => {
        programmingExercise.buildConfig!.buildPlanConfiguration = JSON.stringify(templatePhases);

        comp.loadBuildPhasesTemplate(true);

        expect(buildPhasesTemplateServiceMock.getTemplate).not.toHaveBeenCalled();
    });

    it('should parse existing build plan configuration on init', () => {
        programmingExercise.buildConfig!.buildPlanConfiguration = JSON.stringify(templatePhases);

        comp.ngOnInit();

        expect(comp.buildPlanPhases).toEqual(templatePhases);
    });

    it('should keep default phases on invalid configuration json', () => {
        programmingExercise.buildConfig!.buildPlanConfiguration = 'invalid';

        comp.ngOnInit();

        expect(comp.buildPlanPhases.phases).toHaveLength(1);
        expect(comp.buildPlanPhases.phases[0].script).toBe('# enter the script of this phase');
    });

    it('should update phases when phases editor changes', () => {
        const phases = [{ name: 'test', script: 'npm test', condition: 'ALWAYS' as const, forceRun: false, resultPaths: [] }];

        comp.onPhasesChange(phases);

        expect(comp.buildPlanPhases.phases).toEqual(phases);
    });

    it('should update docker image with trimmed value', () => {
        comp.setDockerImage('  node:20  ');
        expect(comp.buildPlanPhases.dockerImage).toBe('node:20');
    });

    it('should serialize build plan phases to json', () => {
        comp.buildPlanPhases = templatePhases;

        const serialized = comp.getBuildPlanPhasesJSON();

        expect(serialized).toBeDefined();
        expect(JSON.parse(serialized!)).toEqual(templatePhases);
    });
});
