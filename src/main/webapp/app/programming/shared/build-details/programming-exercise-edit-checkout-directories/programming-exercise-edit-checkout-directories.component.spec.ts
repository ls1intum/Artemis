import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseEditCheckoutDirectoriesComponent } from 'app/programming/shared/build-details/programming-exercise-edit-checkout-directories/programming-exercise-edit-checkout-directories.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/programming/shared/entities/build-plan-checkout-directories-dto';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ProgrammingExerciseEditCheckoutDirectoriesComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProgrammingExerciseEditCheckoutDirectoriesComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditCheckoutDirectoriesComponent>;
    const course = { id: 123 } as Course;

    let programmingExercise = new ProgrammingExercise(course, undefined);

    const submissionBuildPlanCheckoutRepositories: BuildPlanCheckoutDirectoriesDTO = {
        exerciseCheckoutDirectory: '/assignment',
        solutionCheckoutDirectory: '/solution',
        testCheckoutDirectory: '/tests',
    };

    beforeEach(async () => {
        programmingExercise = new ProgrammingExercise(course, undefined);
        programmingExercise.customizeBuildPlan = true;

        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseEditCheckoutDirectoriesComponent],
        })
            .overrideComponent(ProgrammingExerciseEditCheckoutDirectoriesComponent, {
                remove: { imports: [HelpIconComponent, TranslateDirective] },
                add: { imports: [MockComponent(HelpIconComponent), MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseEditCheckoutDirectoriesComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.componentRef.setInput('pattern', /^[a-zA-Z0-9_\-/]+$/);
        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', {
            testCheckoutDirectory: '/',
        });
    });

    afterEach(() => {
        fixture.destroy();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('reset should set editable and input fields correctly', () => {
        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', submissionBuildPlanCheckoutRepositories);
        component.reset();
        expect(component.isAssigmentRepositoryEditable).toBe(true);
        expect(component.assignmentCheckoutPath).toBe('assignment');
        expect(component.isTestRepositoryEditable).toBe(true);
        expect(component.testCheckoutPath).toBe('tests');
        expect(component.isSolutionRepositoryEditable).toBe(true);
        expect(component.solutionCheckoutPath).toBe('solution');

        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', {
            testCheckoutDirectory: '/',
        });
        component.reset();
        expect(component.isAssigmentRepositoryEditable).toBe(false);
        expect(component.assignmentCheckoutPath).toBe('');
        expect(component.isTestRepositoryEditable).toBe(false);
        expect(component.testCheckoutPath).toBe('/');
        expect(component.isSolutionRepositoryEditable).toBe(false);
        expect(component.solutionCheckoutPath).toBe('');
    });

    it('should update fields correctly', () => {
        component.onAssigmentRepositoryCheckoutPathChange('assignment');
        expect(component.assignmentCheckoutPath).toBe('assignment');
        expect(component.formValid).toBe(true);
        component.onTestRepositoryCheckoutPathChange('tests');
        expect(component.formValid).toBe(true);
        expect(component.testCheckoutPath).toBe('tests');
        component.onSolutionRepositoryCheckoutPathChange('solution');
        expect(component.formValid).toBe(true);
        expect(component.solutionCheckoutPath).toBe('solution');

        component.onAssigmentRepositoryCheckoutPathChange('solution');
        expect(component.formValid).toBe(false);

        component.calculateFormValid();
    });

    it('should correctly check if values are unique', () => {
        let stringArray: (string | undefined)[] = ['a', 'b', 'c'];
        expect(component.areValuesUnique(stringArray)).toBe(true);

        stringArray = ['a', 'b', 'a'];
        expect(component.areValuesUnique(stringArray)).toBe(false);

        stringArray = ['a', 'b', undefined];
        expect(component.areValuesUnique(stringArray)).toBe(true);
    });

    it('should should reset values correctly when buildconfig is null', () => {
        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(course, undefined));
        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', submissionBuildPlanCheckoutRepositories);
        component.reset();

        expect(component.assignmentCheckoutPath).toBe('assignment');
        expect(component.testCheckoutPath).toBe('tests');
        expect(component.solutionCheckoutPath).toBe('solution');
    });

    it('should set values to their defaults if no buildConfig of submissionBuildPlan available', () => {
        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(course, undefined));
        fixture.componentRef.setInput('submissionBuildPlanCheckoutRepositories', undefined);
        component.reset();

        expect(component.assignmentCheckoutPath).toBe('');
        expect(component.testCheckoutPath).toBe('/');
        expect(component.solutionCheckoutPath).toBe('');
    });
});
