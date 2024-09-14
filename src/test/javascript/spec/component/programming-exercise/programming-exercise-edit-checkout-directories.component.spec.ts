import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseEditCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-edit-checkout-directories/programming-exercise-edit-checkout-directories.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { BuildAction, PlatformAction, ScriptAction } from 'app/entities/programming/build.action';
import { WindFile } from 'app/entities/programming/wind.file';
import { WindMetadata } from 'app/entities/programming/wind.metadata';
import { DockerConfiguration } from 'app/entities/programming/docker.configuration';
import { Course } from 'app/entities/course.model';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/entities/programming/build-plan-checkout-directories-dto';

describe('ProgrammingExerciseEditCheckoutDirectoriesComponent', () => {
    let component: ProgrammingExerciseEditCheckoutDirectoriesComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditCheckoutDirectoriesComponent>;
    const course = { id: 123 } as Course;

    let programmingExercise = new ProgrammingExercise(course, undefined);
    let windFile: WindFile = new WindFile();
    let actions: BuildAction[] = [];
    let gradleBuildAction: ScriptAction = new ScriptAction();
    let cleanBuildAction: ScriptAction = new ScriptAction();
    let platformAction: PlatformAction = new PlatformAction();

    const submissionBuildPlanCheckoutRepositories: BuildPlanCheckoutDirectoriesDTO = {
        exerciseCheckoutDirectory: '/assignment',
        solutionCheckoutDirectory: '/solution',
        testCheckoutDirectory: '/tests',
    };

    beforeEach(async () => {
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
        programmingExercise.buildConfig!.windfile = windFile;

        await TestBed.configureTestingModule({
            imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
            declarations: [ProgrammingExerciseEditCheckoutDirectoriesComponent, MockComponent(HelpIconComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseEditCheckoutDirectoriesComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', {
            testCheckoutDirectory: '/',
        });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('reset should set editable and input fields correctly', () => {
        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', submissionBuildPlanCheckoutRepositories);
        component.reset();
        expect(component.isAssigmentRepositoryEditable).toBeTrue();
        expect(component.assignmentCheckoutPath).toBe('assignment');
        expect(component.isTestRepositoryEditable).toBeTrue();
        expect(component.testCheckoutPath).toBe('tests');
        expect(component.isSolutionRepositoryEditable).toBeTrue();
        expect(component.solutionCheckoutPath).toBe('solution');

        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', {
            testCheckoutDirectory: '/',
        });
        component.reset();
        expect(component.isAssigmentRepositoryEditable).toBeFalse();
        expect(component.assignmentCheckoutPath).toBe('');
        expect(component.isTestRepositoryEditable).toBeFalse();
        expect(component.testCheckoutPath).toBe('/');
        expect(component.isSolutionRepositoryEditable).toBeFalse();
        expect(component.solutionCheckoutPath).toBe('');
    });

    it('should update fields correctly', () => {
        component.onAssigmentRepositoryCheckoutPathChange('assignment');
        expect(component.assignmentCheckoutPath).toBe('assignment');
        expect(component.formValid).toBeTrue();
        component.onTestRepositoryCheckoutPathChange('tests');
        expect(component.formValid).toBeTrue();
        expect(component.testCheckoutPath).toBe('tests');
        component.onSolutionRepositoryCheckoutPathChange('solution');
        expect(component.formValid).toBeTrue();
        expect(component.solutionCheckoutPath).toBe('solution');

        component.onAssigmentRepositoryCheckoutPathChange('solution');
        expect(component.formValid).toBeFalse();

        component.calculateFormValid();
    });

    it('should correctly check if values are unique', () => {
        let stringArray: (string | undefined)[] = ['a', 'b', 'c'];
        expect(component.areValuesUnique(stringArray)).toBeTrue();

        stringArray = ['a', 'b', 'a'];
        expect(component.areValuesUnique(stringArray)).toBeFalse();

        stringArray = ['a', 'b', undefined];
        expect(component.areValuesUnique(stringArray)).toBeTrue();
    });

    it('should should reset values correctly when buildconfig is null', () => {
        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(course, undefined));
        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', submissionBuildPlanCheckoutRepositories);
        component.reset();

        expect(component.assignmentCheckoutPath).toBe('assignment');
        expect(component.testCheckoutPath).toBe('tests');
        expect(component.solutionCheckoutPath).toBe('solution');
    });
});
