// TODO DELTE
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExercisesGroupedByWeekComponent, WEEK_EXERCISE_GROUP_FORMAT_STRING } from 'app/overview/course-exercises/course-exercises-grouped-by-week.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExerciseFilter, SortingAttribute } from 'app/overview/course-exercises/course-exercises.component';
import { Exercise } from 'app/entities/exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { UMLDiagramType } from '@ls1intum/apollon';

const currentExercise_1 = {
    id: 3,
    dueDate: dayjs().add(2, 'days'),
} as TextExercise;

describe('CourseExercisesGroupedByWeekComponent', () => {
    let fixture: ComponentFixture<CourseExercisesGroupedByWeekComponent>;
    let component: CourseExercisesGroupedByWeekComponent;

    let course: Course;
    let exercise: Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExercisesGroupedByWeekComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockComponent(CourseExerciseRowComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExercisesGroupedByWeekComponent);
                component = fixture.componentInstance;

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

    it('should group exercise with individual due date into the week with the individual due date', () => {
        // regular due date is in the past, but the individual one in the future
        const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        newExercise.releaseDate = dayjs('2021-01-10T16:11:00+01:00');
        newExercise.dueDate = dayjs('2021-01-13T16:11:00+01:00');
        const participation = new StudentParticipation();
        participation.individualDueDate = dayjs().add(10, 'days');
        newExercise.studentParticipations = [participation];
        component.filteredAndSortedExercises = [newExercise];
        component.sortingAttribute = SortingAttribute.DUE_DATE;

        component.ngOnChanges();

        const sundayBeforeDueDate = participation.individualDueDate.day(0).format(WEEK_EXERCISE_GROUP_FORMAT_STRING);
        expect(component.exerciseGroups[sundayBeforeDueDate].exercises).toEqual([newExercise]);
    });

    it('should group exercises in correct week categories', () => {
        const exercises = [];
        for (let i = 1; i < 8; i++) {
            const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
            newExercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(i, 'days');
            newExercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(i, 'days');
            exercises.push(newExercise);
        }
        exercises.push(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise);
        component.sortingAttribute = SortingAttribute.DUE_DATE;
        component.filteredAndSortedExercises = exercises;

        component.ngOnChanges();

        expect(Object.keys(component.exerciseGroups)).toContainAllValues(['2021-01-17', '2021-01-10', 'artemisApp.courseOverview.exerciseList.noExerciseDate']);
    });

    it('should contain element for guided tour', () => {
        const guidedTourExerciseContainer = fixture.debugElement.query(By.css('.guided-tour.exercise-row-container')).nativeElement;
        expect(guidedTourExerciseContainer).toBeTruthy();
    });
});
