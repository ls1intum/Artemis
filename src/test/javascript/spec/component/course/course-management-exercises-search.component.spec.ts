import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementExercisesSearchComponent } from 'app/course/manage/course-management-exercises-search.component';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { NgModel } from '@angular/forms';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Exercise } from 'app/entities/exercise.model';

describe('Course Management Exercises Search Component', () => {
    const category1 = new ExerciseCategory();
    category1.category = 'Easy';
    const category2 = new ExerciseCategory();
    category2.category = 'Hard';
    const course: Course = { id: 123 } as Course;
    const exercise1 = new ProgrammingExercise(course, undefined);
    exercise1.id = 1;
    exercise1.title = 'Test Exercise 1';
    exercise1.categories = [category2];
    const exercise2 = new TextExercise(course, undefined);
    exercise2.id = 2;
    exercise2.title = 'Test Exercise 2a';
    exercise2.categories = [category2];
    const exercise3 = new TextExercise(course, undefined);
    exercise3.id = 3;
    exercise3.title = 'Test Exercise 2b';
    exercise3.categories = [category1];
    let exercises: Exercise[] = [];

    let comp: CourseManagementExercisesSearchComponent;
    let fixture: ComponentFixture<CourseManagementExercisesSearchComponent>;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseManagementExercisesSearchComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CourseExerciseCardComponent),
                MockDirective(TranslateDirective),
                MockDirective(NgModel),
            ],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseManagementExercisesSearchComponent);
        comp = fixture.componentInstance;
        exercises = [exercise1, exercise2, exercise3];
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseManagementExercisesSearchComponent).toBeDefined();
    });

    it('should have empty filter on init', () => {
        comp.ngOnInit();
        expect(comp.exerciseNameSearch).toEqual('');
        expect(comp.exerciseCategorySearch).toEqual('');
        expect(comp.exerciseTypeSearch).toEqual('all');
    });

    it('should change filter on name change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseNameSearch = 'test';
        const emitSpy = jest.spyOn(comp.exerciseFilter, 'emit');
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseNameSearch = filter.exerciseNameSearch;
        button.click();
        expect(emitSpy).toHaveBeenCalled();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });

    it('should change filter on category change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseCategorySearch = 'homework';
        const emitSpy = jest.spyOn(comp.exerciseFilter, 'emit');
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseCategorySearch = filter.exerciseCategorySearch;
        button.click();
        expect(emitSpy).toHaveBeenCalled();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });

    it('should change filter on type change', () => {
        const filter = new ExerciseFilter();
        filter.exerciseTypeSearch = 'programming';
        const emitSpy = jest.spyOn(comp.exerciseFilter, 'emit');
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#saveFilterButton');
        comp.exerciseTypeSearch = filter.exerciseTypeSearch;
        button.click();
        expect(emitSpy).toHaveBeenCalled();
        expect(emitSpy).toHaveBeenCalledWith(filter);
    });

    describe('Exercise Filter Test', () => {
        it('should be empty on create', () => {
            const filter = new ExerciseFilter();
            expect(filter.isEmpty()).toEqual(true);
        });

        it('should filter by name', () => {
            const filter = new ExerciseFilter();
            filter.exerciseNameSearch = '2';
            const filteredExercises = exercises.filter((exercise) => filter.includeExercise(exercise));
            expect(filteredExercises.length).toEqual(2);
        });

        it('should filter by category', () => {
            const filter = new ExerciseFilter();
            filter.exerciseCategorySearch = 'easy';
            const filteredExercises = exercises.filter((exercise) => filter.includeExercise(exercise));
            expect(filteredExercises.length).toEqual(1);
        });

        it('should filter by type', () => {
            const filter = new ExerciseFilter();
            filter.exerciseTypeSearch = 'text';
            const filteredExercises = exercises.filter((exercise) => filter.includeExercise(exercise));
            expect(filteredExercises.length).toEqual(2);
        });

        it('should filter by all', () => {
            const filter = new ExerciseFilter();
            filter.exerciseNameSearch = 'a';
            filter.exerciseCategorySearch = 'hard';
            filter.exerciseTypeSearch = 'text';
            const filteredExercises = exercises.filter((exercise) => filter.includeExercise(exercise));
            expect(filteredExercises.length).toEqual(1);
        });
    });
});
