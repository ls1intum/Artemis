import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExercisesGroupedByWeekComponent } from 'app/overview/course-exercises/course-exercises-grouped-by-week.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExerciseFilter, ExerciseWithDueDate } from 'app/overview/course-exercises/course-exercises.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise } from 'app/entities/exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';

const currentExercise_1 = {
    id: 3,
    dueDate: dayjs().add(2, 'days'),
} as TextExercise;

describe('CourseExercisesGroupedByWeekComponent', () => {
    let fixture: ComponentFixture<CourseExercisesGroupedByWeekComponent>;
    let component: CourseExercisesGroupedByWeekComponent;
    let exerciseService: ExerciseService;

    let course: Course;
    let exercise: Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExercisesGroupedByWeekComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockComponent(CourseExerciseRowComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExercisesGroupedByWeekComponent);
                component = fixture.componentInstance;
                exerciseService = TestBed.inject(ExerciseService);

                course = new Course();
                course.id = 123;
                exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
                exercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(1, 'days');
                exercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(1, 'days');
                course.exercises = [exercise];
                course.unenrollmentEnabled = true;
                course.unenrollmentEndDate = dayjs().add(1, 'days');

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        component.ngOnInit();
        component.ngOnChanges();
        expect(component).toBeTruthy();
    });

    describe('isVisibleToStudents', () => {
        it('should be called if nextRelevantExercise is present', () => {
            const isVisibleToStudentsSpy = jest.spyOn(component, 'isVisibleToStudents');
            component.activeFilters = new Set<ExerciseFilter>([]);
            component.nextRelevantExercise = {
                exercise: currentExercise_1,
                dueDate: currentExercise_1.dueDate,
            } as ExerciseWithDueDate;

            fixture.detectChanges();

            expect(isVisibleToStudentsSpy).toHaveBeenCalled();
        });

        it('should return true if filter is not UNRELEASED', () => {
            const exercise = currentExercise_1;
            component.activeFilters = new Set<ExerciseFilter>([]);
            const isVisibleToStudentsResult = component.isVisibleToStudents(exercise);

            expect(isVisibleToStudentsResult).toBeTrue();
        });

        it('should return false if filter is UNRELEASED', () => {
            const exercise = currentExercise_1;
            component.activeFilters = new Set<ExerciseFilter>([ExerciseFilter.UNRELEASED]);
            const isVisibleToStudentsResult = component.isVisibleToStudents(exercise);

            expect(isVisibleToStudentsResult).toBeFalsy();
        });

        it('should return true for a QuizExercise with visibleToStudents=true', () => {
            const quizExercise = {
                id: 9,
                dueDate: dayjs().add(3, 'days'),
                visibleToStudents: true,
            } as QuizExercise;
            component.activeFilters = new Set<ExerciseFilter>([ExerciseFilter.UNRELEASED]);
            const isVisibleToStudentsResult = component.isVisibleToStudents(quizExercise);

            expect(isVisibleToStudentsResult).toBeTrue();
        });

        it('should return false for a QuizExercise with visibleToStudents=false', () => {
            const quizExercise = {
                id: 10,
                dueDate: dayjs().add(3, 'days'),
                visibleToStudents: false,
            } as QuizExercise;
            component.activeFilters = new Set<ExerciseFilter>([ExerciseFilter.UNRELEASED]);
            const isVisibleToStudentsResult = component.isVisibleToStudents(quizExercise);

            expect(isVisibleToStudentsResult).toBeFalse();
        });
    });

    it('should react to changes', () => {
        jest.spyOn(exerciseService, 'getNextExerciseForHours').mockReturnValue(exercise);
        component.ngOnChanges();
        const expectedExercise = {
            exercise,
            dueDate: exercise.dueDate,
        };
        expect(component.nextRelevantExercise).toEqual(expectedExercise);
    });
});
