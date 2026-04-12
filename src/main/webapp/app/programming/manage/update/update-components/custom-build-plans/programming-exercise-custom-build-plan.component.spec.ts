import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BuildAction, PlatformAction, ScriptAction } from 'app/programming/shared/entities/build.action';
import { DockerConfiguration } from 'app/programming/shared/entities/docker.configuration';
import { WindFile } from 'app/programming/shared/entities/wind.file';
import { WindMetadata } from 'app/programming/shared/entities/wind.metadata';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Renderer2 } from '@angular/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { AeolusService } from 'app/programming/shared/services/aeolus.service';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-build-plan.component';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { Observable } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { BuildPhasesEditorComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phases-editor.component';
import { BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ProgrammingExercise Custom Build Plan', () => {
    let fixture: ComponentFixture<ProgrammingExerciseCustomBuildPlanComponent>;
    let comp: ProgrammingExerciseCustomBuildPlanComponent;
    const course = { id: 123 } as Course;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
    let programmingExercise = new ProgrammingExercise(course, undefined);
    let windfile: WindFile = new WindFile();
    let actions: BuildAction[] = [];
    let gradleBuildAction: ScriptAction = new ScriptAction();
    let cleanBuildAction: ScriptAction = new ScriptAction();
    let platformAction: PlatformAction = new PlatformAction();
    let mockAeolusService: AeolusService;

    beforeEach(() => {
        programmingExercise = new ProgrammingExercise(course, undefined);
        programmingExercise.customizeBuildPlanWithAeolus = true;
        windfile = new WindFile();
        const metadata = new WindMetadata();
        metadata.docker = new DockerConfiguration();
        metadata.docker.image = 'testImage';
        windfile.metadata = metadata;
        actions = [];
        gradleBuildAction = new ScriptAction();
        gradleBuildAction.name = 'gradle';
        gradleBuildAction.script = './gradlew clean test';
        gradleBuildAction.results = [{ name: 'test', path: '**/test-results.xml', ignore: '' }];
        platformAction = new PlatformAction();
        platformAction.name = 'platform';
        platformAction.kind = 'junit';
        cleanBuildAction = new ScriptAction();
        cleanBuildAction.name = 'clean';
        cleanBuildAction.script = `chmod -R 777 .`;
        actions.push(gradleBuildAction);
        actions.push(cleanBuildAction);
        actions.push(platformAction);
        windfile.actions = actions;
        programmingExercise.buildConfig!.windfile = windfile;

        TestBed.configureTestingModule({
            imports: [ProgrammingExerciseCustomBuildPlanComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                Renderer2,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
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
            .compileComponents()
            .then(() => {
                mockAeolusService = TestBed.inject(AeolusService);
            });

        fixture = TestBed.createComponent(ProgrammingExerciseCustomBuildPlanComponent);
        comp = fixture.componentInstance;
        comp.programmingExercise = programmingExercise;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should return false to reload template', () => {
        comp.programmingLanguage = programmingExercise.programmingLanguage;
        comp.projectType = programmingExercise.projectType;
        comp.sequentialTestRuns = programmingExercise.buildConfig?.sequentialTestRuns;
        comp.staticCodeAnalysisEnabled = programmingExercise.staticCodeAnalysisEnabled;
        expect(comp.shouldReloadTemplate()).toBeFalse();
    });

    it('should return true to reload template', () => {
        comp.programmingLanguage = ProgrammingLanguage.JAVA;
        comp.projectType = ProjectType.PLAIN_GRADLE;
        comp.sequentialTestRuns = programmingExercise.buildConfig?.sequentialTestRuns;
        comp.staticCodeAnalysisEnabled = true;
        expect(comp.shouldReloadTemplate()).toBeTrue();
    });

    it('should reset buildplan', () => {
        programmingExercise.buildConfig!.windfile = windfile;
        programmingExercise.buildConfig!.buildPlanConfiguration = 'some build plan';
        expect(programmingExercise.buildConfig?.windfile).toBeDefined();
        expect(programmingExercise.buildConfig?.buildPlanConfiguration).toBeDefined();
        comp.resetCustomBuildPlan();
        expect(programmingExercise.buildConfig?.windfile).toBeUndefined();
        expect(programmingExercise.buildConfig?.buildPlanConfiguration).toBeUndefined();
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
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExerciseCreationConfig.customBuildPlansSupported = PROFILE_LOCALCI;
        comp.loadAeolusTemplate();
        expect(comp.programmingLanguage).toBe(programmingExercise.programmingLanguage);
        expect(comp.projectType).toBe(programmingExercise.projectType);
        expect(comp.sequentialTestRuns).toBe(programmingExercise.buildConfig?.sequentialTestRuns);
        expect(comp.staticCodeAnalysisEnabled).toBe(programmingExercise.staticCodeAnalysisEnabled);
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

    it('should update programming exercise values', () => {
        comp.programmingExercise.buildConfig!.windfile = undefined;
        programmingExerciseCreationConfigMock.customBuildPlansSupported = PROFILE_LOCALCI;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.next(mockAeolusService.serializeWindFile(windfile))));
        comp.loadAeolusTemplate();
        expect(comp.programmingExercise.buildConfig?.windfile).toBeDefined();
        expect(comp.programmingExercise.buildConfig?.timeoutSeconds).toBe(0);
    });

    it('should call this.resetCustomBuildPlan', () => {
        comp.programmingExercise.buildConfig!.windfile = undefined;
        programmingExerciseCreationConfigMock.customBuildPlansSupported = PROFILE_LOCALCI;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        const resetSpy = jest.spyOn(comp, 'resetCustomBuildPlan');
        jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.error('error')));
        comp.loadAeolusTemplate();
        expect(comp.programmingExercise.buildConfig?.windfile).toBeUndefined();
        expect(resetSpy).toHaveBeenCalled();
    });

    it('should set docker image correctly', () => {
        comp.programmingExercise.buildConfig!.windfile = windfile;
        comp.programmingExercise.buildConfig!.windfile.metadata.docker.image = 'old';
        comp.setDockerImage('testImage');
        expect(comp.buildPlanPhases.dockerImage!).toBe('testImage');
    });

    it('should not call getAeolusTemplateFile when import from file if windfile present', () => {
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExerciseCreationConfig.isImportFromFile = true;
        programmingExercise.buildConfig!.windfile = windfile;
        jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.next(mockAeolusService.serializeWindFile(windfile))));
        comp.ngOnChanges({
            programmingExercise: {
                currentValue: programmingExercise,
                previousValue: undefined,
                firstChange: false,
                isFirstChange: function (): boolean {
                    throw new Error('Function not implemented.');
                },
            },
            programmingExerciseCreationConfig: {
                currentValue: JSON.parse(JSON.stringify(comp.programmingExerciseCreationConfig)),
                previousValue: undefined,
                firstChange: false,
                isFirstChange: function (): boolean {
                    throw new Error('Function not implemented.');
                },
            },
        });
        expect(mockAeolusService.getAeolusTemplateFile).not.toHaveBeenCalled();
    });

    it('should set timeout correctly', () => {
        comp.programmingExercise.buildConfig!.timeoutSeconds = 100;
        comp.setTimeout(10);
        expect(comp.programmingExercise.buildConfig?.timeoutSeconds).toBe(10);
    });

    describe('ngOnInit', () => {
        it('should parse buildPlanConfiguration JSON into buildPlanPhases', () => {
            const phases: BuildPlanPhases = {
                phases: [{ name: 'test', script: 'npm test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
                dockerImage: 'node:18',
            };
            programmingExercise.buildConfig!.buildPlanConfiguration = JSON.stringify(phases);
            comp.ngOnInit();

            expect(comp.buildPlanPhases.phases).toHaveLength(1);
            expect(comp.buildPlanPhases.phases[0].name).toBe('test');
            expect(comp.buildPlanPhases.dockerImage).toBe('node:18');
        });

        it('should fallback to windfile when buildPlanConfiguration is not valid JSON', () => {
            programmingExercise.buildConfig!.buildPlanConfiguration = 'invalid json';
            programmingExercise.buildConfig!.windfile = windfile;
            comp.ngOnInit();

            expect(comp.buildPlanPhases.phases).toHaveLength(2); // gradle + clean actions (platformAction is filtered)
            expect(comp.buildPlanPhases.dockerImage).toBe('testImage');
        });

        it('should fallback to windfile when buildPlanConfiguration has no phases', () => {
            programmingExercise.buildConfig!.buildPlanConfiguration = JSON.stringify({ phases: [] });
            programmingExercise.buildConfig!.windfile = windfile;
            comp.ngOnInit();

            expect(comp.buildPlanPhases.phases).toHaveLength(2);
        });

        it('should convert windfile actions to phases format', () => {
            programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
            programmingExercise.buildConfig!.windfile = windfile;
            comp.ngOnInit();

            const phases = comp.buildPlanPhases.phases;
            expect(phases[0].name).toBe('gradle');
            expect(phases[0].script).toBe('./gradlew clean test');
            expect(phases[0].resultPaths).toContain('**/test-results.xml');
            expect(phases[1].name).toBe('clean');
        });

        it('should wrap converted windfile script with workdir cd commands', () => {
            gradleBuildAction.workdir = '${testWorkingDirectory}';
            programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
            programmingExercise.buildConfig!.windfile = windfile;

            comp.ngOnInit();

            expect(comp.buildPlanPhases.phases[0].script).toBe('ORIGINAL_DIR="$(pwd)"\ncd "tests"\n./gradlew clean test\ncd "$ORIGINAL_DIR"');
        });

        it('should use default phases when no configuration exists', () => {
            programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
            programmingExercise.buildConfig!.windfile = undefined;
            comp.ngOnInit();

            expect(comp.buildPlanPhases.phases).toHaveLength(1);
            expect(comp.buildPlanPhases.phases[0].name).toBe('');
            expect(comp.buildPlanPhases.phases[0].script).toBe('# enter the script of this phase');
        });
    });

    describe('onPhasesChange', () => {
        it('should update buildPlanPhases with new phases', () => {
            const newPhases: BuildPhase[] = [
                { name: 'build', script: 'npm build', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
                { name: 'test', script: 'npm test', condition: 'AFTER_DUE_DATE', forceRun: false, resultPaths: ['**/results.xml'] },
            ];

            comp.onPhasesChange(newPhases);

            expect(comp.buildPlanPhases.phases).toEqual(newPhases);
        });

        it('should preserve dockerImage when updating phases', () => {
            comp.buildPlanPhases = { phases: [], dockerImage: 'original-image' };
            const newPhases: BuildPhase[] = [{ name: 'test', script: 'test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];

            comp.onPhasesChange(newPhases);

            expect(comp.buildPlanPhases.dockerImage).toBe('original-image');
            expect(comp.buildPlanPhases.phases).toEqual(newPhases);
        });
    });

    describe('setDockerImage', () => {
        it('should update dockerImage in buildPlanPhases', () => {
            comp.buildPlanPhases = { phases: [], dockerImage: 'old-image' };
            comp.setDockerImage('new-image');

            expect(comp.buildPlanPhases.dockerImage).toBe('new-image');
        });

        it('should trim whitespace from docker image', () => {
            comp.setDockerImage('  image-with-spaces  ');
            expect(comp.buildPlanPhases.dockerImage).toBe('image-with-spaces');
        });

        it('should preserve phases when updating docker image', () => {
            const phases: BuildPhase[] = [{ name: 'test', script: 'test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];
            comp.buildPlanPhases = { phases, dockerImage: 'old' };

            comp.setDockerImage('new');

            expect(comp.buildPlanPhases.phases).toEqual(phases);
        });
    });

    describe('getBuildPlanPhasesJSON', () => {
        it('should return JSON string of buildPlanPhases', () => {
            const phases: BuildPhase[] = [{ name: 'test', script: 'npm test', condition: 'ALWAYS', forceRun: false, resultPaths: ['**/results.xml'] }];
            comp.buildPlanPhases = { phases, dockerImage: 'node:18' };

            const json = comp.getBuildPlanPhasesJSON();
            const parsed = JSON.parse(json!);

            expect(parsed.phases).toEqual(phases);
            expect(parsed.dockerImage).toBe('node:18');
        });

        it('should return undefined when phases array is empty', () => {
            comp.buildPlanPhases = { phases: [] };
            expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
        });

        it('should return undefined when buildPlanPhases is undefined', () => {
            comp.buildPlanPhases = undefined as any;
            expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
        });

        it('should return undefined when phase names contain invalid characters', () => {
            const phases: BuildPhase[] = [{ name: 'bad name', script: 'npm test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];
            comp.buildPlanPhases = { phases, dockerImage: 'node:18' };

            expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
        });

        it('should return undefined when phase name starts with number', () => {
            const phases: BuildPhase[] = [{ name: '1build', script: 'npm test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];
            comp.buildPlanPhases = { phases, dockerImage: 'node:18' };

            expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
        });

        it('should return undefined when phase names contain hyphen', () => {
            const phases: BuildPhase[] = [{ name: 'build-phase', script: 'npm test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];
            comp.buildPlanPhases = { phases, dockerImage: 'node:18' };

            expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
        });

        it('should return undefined for reserved phase names case-insensitively', () => {
            const phases: BuildPhase[] = [{ name: 'MAIN', script: 'npm test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];
            comp.buildPlanPhases = { phases, dockerImage: 'node:18' };

            expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
        });

        it('should return undefined when phase names are duplicates case-insensitively', () => {
            const phases: BuildPhase[] = [
                { name: 'Build', script: 'npm build', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
                { name: 'build', script: 'npm test', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
            ];
            comp.buildPlanPhases = { phases, dockerImage: 'node:18' };

            expect(comp.getBuildPlanPhasesJSON()).toBeUndefined();
        });
    });

    describe('replacePlaceholders', () => {
        it('should replace studentParentWorkingDirectoryName placeholder', () => {
            const script = 'cd ${studentParentWorkingDirectoryName} && npm build';
            const result = comp.replacePlaceholders(script);

            expect(result).toContain('assignment');
            expect(result).not.toContain('${studentParentWorkingDirectoryName}');
        });

        it('should replace testWorkingDirectory placeholder', () => {
            const script = 'cd ${testWorkingDirectory} && npm test';
            const result = comp.replacePlaceholders(script);

            expect(result).toContain('tests');
            expect(result).not.toContain('${testWorkingDirectory}');
        });

        it('should use custom checkout paths when configured', () => {
            comp.programmingExercise.buildConfig!.assignmentCheckoutPath = 'custom-assignment';
            comp.programmingExercise.buildConfig!.testCheckoutPath = 'custom-tests';

            const script = '${studentParentWorkingDirectoryName} ${testWorkingDirectory}';
            const result = comp.replacePlaceholders(script);

            expect(result).toBe('custom-assignment custom-tests');
        });
    });

    describe('loadAeolusTemplate', () => {
        it('should populate buildPlanPhases from windfile template', () => {
            comp.programmingExercise.buildConfig!.windfile = undefined;
            programmingExerciseCreationConfigMock.customBuildPlansSupported = PROFILE_LOCALCI;
            comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;

            jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.next(mockAeolusService.serializeWindFile(windfile))));

            comp.loadAeolusTemplate();

            expect(comp.buildPlanPhases.phases.length).toBeGreaterThan(0);
            expect(comp.buildPlanPhases.dockerImage).toBe('testImage');
        });

        it('should set buildPlanLoaded flag', () => {
            comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
            comp.programmingExerciseCreationConfig.buildPlanLoaded = false;

            jest.spyOn(mockAeolusService, 'getAeolusTemplateFile').mockReturnValue(new Observable((subscriber) => subscriber.next(mockAeolusService.serializeWindFile(windfile))));

            comp.loadAeolusTemplate();

            expect(comp.programmingExerciseCreationConfig.buildPlanLoaded).toBeTrue();
        });
    });
});
