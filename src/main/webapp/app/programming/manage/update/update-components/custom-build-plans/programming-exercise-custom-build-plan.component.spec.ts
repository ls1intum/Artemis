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
import { LegacyBuildPlanConverterService } from 'app/programming/shared/services/legacy-build-plan-converter.service';

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
        jest.restoreAllMocks();
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

    it('should convert legacy build plan on init when no phases config exists', () => {
        const legacyPhases: BuildPlanPhases = {
            phases: [{ name: 'script', script: 'legacy', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
            dockerImage: 'legacy:1.0',
        };
        programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
        programmingExercise.buildConfig!.buildScript = './gradlew test';
        programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        // Mock the legacy converter
        const legacyService = TestBed.inject(LegacyBuildPlanConverterService);
        jest.spyOn(legacyService, 'convertLegacyBuildPlanConfiguration').mockReturnValue(legacyPhases);
        comp.ngOnInit();
        expect(comp.buildPlanPhases).toEqual(legacyPhases);
    });

    it('should reset when legacy conversion fails', () => {
        programmingExercise.buildConfig!.buildPlanConfiguration = 'not undefined';
        programmingExercise.buildConfig!.buildScript = './gradlew test';
        programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        const legacyService = TestBed.inject(LegacyBuildPlanConverterService);
        jest.spyOn(legacyService, 'convertLegacyBuildPlanConfiguration').mockReturnValue(undefined);
        comp.ngOnInit();
        expect(programmingExercise.buildConfig?.buildPlanConfiguration).toBeUndefined();
        expect(programmingExercise.buildConfig?.buildScript).toBeUndefined();
    });

    it('should reset when no build script exists on init', () => {
        programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
        programmingExercise.buildConfig!.buildScript = undefined;
        comp.ngOnInit();
        expect(programmingExercise.buildConfig?.buildPlanConfiguration).toBeUndefined();
    });

    it('should fall through to legacy when parsed phases are empty', () => {
        programmingExercise.buildConfig!.buildPlanConfiguration = JSON.stringify({ phases: [] });
        programmingExercise.buildConfig!.buildScript = undefined;
        comp.ngOnInit();
        expect(programmingExercise.buildConfig?.buildPlanConfiguration).toBeUndefined();
    });

    it('should return undefined from getBuildPlanPhasesJSON when names are invalid', () => {
        comp.buildPlanPhases = {
            phases: [{ name: 'bad name', script: 'test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
        };
        expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
    });

    it('should return undefined from getBuildPlanPhasesJSON for reserved phase names', () => {
        comp.buildPlanPhases = {
            phases: [{ name: 'main', script: 'test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
        };
        expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
    });

    it('should return undefined from getBuildPlanPhasesJSON for duplicate names', () => {
        comp.buildPlanPhases = {
            phases: [
                { name: 'Build', script: 'a', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
                { name: 'build', script: 'b', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
            ],
        };
        expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
    });

    it('should return undefined from getBuildPlanPhasesJSON when phases are empty', () => {
        comp.buildPlanPhases = { phases: [] };
        expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
    });
});
