import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { By } from '@angular/platform-browser';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { CourseExercisesComponent, ExerciseFilter, ExerciseSortingOrder, SortingAttribute } from 'app/overview/course-exercises/course-exercises.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ActivatedRoute } from '@angular/router';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { FormsModule } from '@angular/forms';

describe('CourseExercisesComponent', () => {
    let fixture: ComponentFixture<CourseExercisesComponent>;
    let component: CourseExercisesComponent;
    let service: CourseManagementService;
    let courseCalculation: CourseScoreCalculationService;
    let exerciseService: ExerciseService;
    let localStorageService: LocalStorageService;

    let course: Course;
    let exercise: Exercise;
    let courseCalculationSpy: jest.SpyInstance;

    const parentRoute = { params: of({ courseId: '123' }) } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, RouterTestingModule.withRoutes([])],
            declarations: [
                CourseExercisesComponent,
                MockDirective(OrionFilterDirective),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(SidePanelComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(SortByDirective),
                TranslatePipeMock,
                MockDirective(SortDirective),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockTranslateValuesDirective,
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExercisesComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
                courseCalculation = TestBed.inject(CourseScoreCalculationService);
                exerciseService = TestBed.inject(ExerciseService);
                localStorageService = TestBed.inject(LocalStorageService);

                course = new Course();
                course.id = 123;
                exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
                exercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(1, 'days');
                exercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(1, 'days');
                course.exercises = [exercise];
                jest.spyOn(service, 'getCourseUpdates').mockReturnValue(of(course));
                jest.spyOn(localStorageService, 'retrieve').mockReturnValue('OVERDUE,NEEDS_WORK');
                courseCalculationSpy = jest.spyOn(courseCalculation, 'getCourse').mockReturnValue(course);

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component.course).toEqual(course);
        expect(courseCalculationSpy.mock.calls).toHaveLength(2);
        expect(courseCalculationSpy.mock.calls[0][0]).toBe(course.id);
        expect(courseCalculationSpy.mock.calls[1][0]).toBe(course.id);
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

    it('should reorder all exercises', () => {
        const oldExercise = {
            diagramType: UMLDiagramType.ClassDiagram,
            course,
            exerciseGroup: undefined,
            releaseDate: dayjs('2021-01-13T16:11:00+01:00'),
            dueDate: dayjs('2021-01-13T16:12:00+01:00'),
        } as ModelingExercise;
        const evenOlderExercise = {
            diagramType: UMLDiagramType.ClassDiagram,
            course,
            exerciseGroup: undefined,
            releaseDate: dayjs('2021-01-07T16:11:00+01:00'),
            dueDate: dayjs('2021-01-07T16:12:00+01:00'),
        } as ModelingExercise;
        component.course!.exercises = [oldExercise, evenOlderExercise];
        component.sortingOrder = ExerciseSortingOrder.DESC;
        component.activeFilters.clear();
        component.activeFilters.add(ExerciseFilter.NEEDS_WORK);

        component.flipOrder();

        expect(component.sortingOrder).toBe(ExerciseSortingOrder.ASC);
        expect(component.weeklyIndexKeys).toEqual(['2021-01-03', '2021-01-10']);

        component.flipOrder();

        expect(component.sortingOrder).toBe(ExerciseSortingOrder.DESC);
        expect(component.weeklyIndexKeys).toEqual(['2021-01-10', '2021-01-03']);
    });

    it('should filter all exercises with upcoming release date', () => {
        // No filters should be set initially
        component.activeFilters.clear();
        expect(component.activeFilters).toEqual(new Set());

        // In the following, visibleToStudents is set manually to the corresponding
        // value. This is usually computed by the server
        // This exercise should be filtered, since the release date is in the future
        const newModelingExerciseWithFutureReleaseDate = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        newModelingExerciseWithFutureReleaseDate.releaseDate = dayjs().add(1, 'days');
        (newModelingExerciseWithFutureReleaseDate as QuizExercise).visibleToStudents = false;

        // This exercise should not be filtered, since the release date is in the past
        const newModelingExerciseWithPastReleaseDate = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        newModelingExerciseWithPastReleaseDate.releaseDate = dayjs().subtract(1, 'days');
        (newModelingExerciseWithPastReleaseDate as QuizExercise).visibleToStudents = true;

        // This exercise should be filtered, since the release date is in the future
        const newTextExerciseWithFutureReleaseDate = new TextExercise(course, undefined) as Exercise;
        newTextExerciseWithFutureReleaseDate.releaseDate = dayjs().add(1, 'days');
        (newTextExerciseWithFutureReleaseDate as QuizExercise).visibleToStudents = false;

        // This exercise should not be filtered, since the release date is in the past
        const newTextExerciseWithPastReleaseDate = new TextExercise(course, undefined) as Exercise;
        newTextExerciseWithPastReleaseDate.releaseDate = dayjs().subtract(1, 'days');
        (newTextExerciseWithPastReleaseDate as QuizExercise).visibleToStudents = true;

        // Adding the created exercises to the course exercises
        component.course!.exercises = [
            newModelingExerciseWithFutureReleaseDate,
            newModelingExerciseWithPastReleaseDate,
            newTextExerciseWithFutureReleaseDate,
            newTextExerciseWithPastReleaseDate,
        ];

        // Number of exercises in the course must be 4, since we have added 4 exercises
        expect(component.course!.exercises).toHaveLength(4);
        component.toggleFilters([ExerciseFilter.UNRELEASED]);

        // Number of active modeling and text exercises must be 1 respectively, since we have filtered
        // the exercises with release date in the future
        expect(component.exerciseCountMap.get('modeling')).toBe(1);
        expect(component.exerciseCountMap.get('text')).toBe(1);

        component.toggleFilters([ExerciseFilter.UNRELEASED]);

        // Number of active modeling and text exercises must be 2 respectively, since we do not filter
        // the exercises with release date in the future anymore
        expect(component.exerciseCountMap.get('modeling')).toBe(2);
        expect(component.exerciseCountMap.get('text')).toBe(2);
    });

    it('should filter all exercises in different situations', () => {
        component.sortingOrder = ExerciseSortingOrder.DESC;
        const filters: ExerciseFilter[] = [ExerciseFilter.OVERDUE, ExerciseFilter.NEEDS_WORK];
        const localStorageSpy = jest.spyOn(localStorageService, 'store');

        component.toggleFilters(filters);

        expect(localStorageSpy).toHaveBeenCalledOnce();
        expect(component.activeFilters).toEqual(new Set());

        for (let i = 1; i < 8; i++) {
            const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
            newExercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(i, 'days');
            newExercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(i, 'days');
            component.course!.exercises![i] = newExercise;
        }
        component.course!.exercises![component.course!.exercises!.length] = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;

        component.activeFilters.clear();
        component.activeFilters.add(ExerciseFilter.OVERDUE);

        component.toggleFilters(filters);

        expect(component.activeFilters).toEqual(new Set().add(ExerciseFilter.NEEDS_WORK));
        expect(Object.keys(component.weeklyExercisesGrouped)).toEqual(['2021-01-17', '2021-01-10', 'noDate']);
        expect(component.weeklyIndexKeys).toEqual(['2021-01-17', '2021-01-10', 'noDate']);
        expect(component.exerciseCountMap.get('modeling')).toBe(9);

        // trigger updateUpcomingExercises dynamically with dayjs()
        component.course!.exercises = [];
        for (let i = 0; i < 7; i++) {
            const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
            newExercise.dueDate = dayjs().add(1 + i, 'days');
            newExercise.releaseDate = dayjs().subtract(1 + i, 'days');
            component.course!.exercises[i] = newExercise;
        }

        component.toggleFilters(filters);

        expect(component.upcomingExercises).toHaveLength(5);
    });

    it('should filter optional exercises', () => {
        component.activeFilters = new Set();

        for (let i = 0; i < 4; i++) {
            const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
            newExercise.includedInOverallScore = (i + 1) % 2 === 0 ? IncludedInOverallScore.NOT_INCLUDED : IncludedInOverallScore.INCLUDED_COMPLETELY;
            component.course!.exercises![i] = newExercise;
        }

        component.toggleFilters([ExerciseFilter.OPTIONAL]);
        fixture.detectChanges();

        expect(component.activeFilters).toEqual(new Set().add(ExerciseFilter.OPTIONAL));
        expect(component.numberOfExercises).toBe(2);

        component.toggleFilters([ExerciseFilter.OPTIONAL]);
        fixture.detectChanges();

        expect(component.activeFilters).toEqual(new Set());
        expect(component.numberOfExercises).toBe(4);
    });

    it('should not filter exercises with an individual due date after the current date', () => {
        component.sortingOrder = ExerciseSortingOrder.DESC;

        // regular due date is in the past, but the individual one in the future
        const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        newExercise.releaseDate = dayjs('2021-01-10T16:11:00+01:00');
        newExercise.dueDate = dayjs('2021-01-13T16:11:00+01:00');
        const participation = new StudentParticipation();
        participation.individualDueDate = dayjs().add(10, 'days');
        newExercise.studentParticipations = [participation];

        component.course!.exercises![1] = newExercise;

        component.activeFilters.clear();
        component.toggleFilters([ExerciseFilter.OVERDUE]);

        expect(component.activeFilters).toEqual(new Set().add(ExerciseFilter.OVERDUE));
        expect(component.exerciseCountMap.get('modeling')).toBe(1);

        // the exercise should be grouped into the week with the individual due date
        const sundayBeforeDueDate = participation.individualDueDate.day(0).format('YYYY-MM-DD');
        expect(component.weeklyExercisesGrouped[sundayBeforeDueDate].exercises).toEqual([newExercise]);
    });

    it('should apply filters to the next relevant exercise', () => {
        const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        newExercise.releaseDate = dayjs().subtract(3, 'hours');
        newExercise.dueDate = dayjs().add(3, 'hours');
        newExercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;

        component.course!.exercises = [newExercise];

        component.ngOnChanges();

        expect(component.nextRelevantExercise).toEqual({
            exercise: newExercise,
            dueDate: newExercise.dueDate,
        });

        component.toggleFilters([ExerciseFilter.OPTIONAL]);

        expect(component.nextRelevantExercise).toBeUndefined();
    });

    it('should sort upcoming exercises by ascending individual due dates', () => {
        const exerciseRegularDueDate = new ModelingExercise(UMLDiagramType.ActivityDiagram, course, undefined);
        exerciseRegularDueDate.releaseDate = dayjs().add(10, 'days');
        const dueDate1 = dayjs().add(11, 'days');
        exerciseRegularDueDate.dueDate = dueDate1;
        const participationRegularDueDate = new StudentParticipation(ParticipationType.STUDENT);
        exerciseRegularDueDate.studentParticipations = [participationRegularDueDate];

        const exerciseIndividualDueDate = new ModelingExercise(UMLDiagramType.ActivityDiagram, course, undefined);
        exerciseIndividualDueDate.releaseDate = dayjs().add(5, 'days');
        // regular due date before the due date of the other exercise
        exerciseIndividualDueDate.dueDate = dayjs().add(7, 'days');
        const participationIndividualDueDate = new StudentParticipation(ParticipationType.STUDENT);
        // individual due date later than the due date of the other exercise
        const dueDate2 = dayjs().add(20, 'days');
        participationIndividualDueDate.individualDueDate = dueDate2;
        exerciseIndividualDueDate.studentParticipations = [participationIndividualDueDate];

        const checkUpcomingExercises = () => {
            const expectedUpcomingExercises = [
                { exercise: exerciseRegularDueDate, dueDate: dueDate1 },
                { exercise: exerciseIndividualDueDate, dueDate: dueDate2 },
            ];
            expect(component.upcomingExercises).toEqual(expectedUpcomingExercises);
        };

        component.course!.exercises! = [exerciseIndividualDueDate, exerciseRegularDueDate];

        // the sidebar should always be sorted by ascending due date
        component.sortingOrder = ExerciseSortingOrder.DESC;
        component.setSortingAttribute(SortingAttribute.DUE_DATE);
        checkUpcomingExercises();

        component.sortingOrder = ExerciseSortingOrder.ASC;
        component.setSortingAttribute(SortingAttribute.DUE_DATE);
        checkUpcomingExercises();

        component.sortingOrder = ExerciseSortingOrder.DESC;
        component.setSortingAttribute(SortingAttribute.RELEASE_DATE);
        checkUpcomingExercises();

        component.sortingOrder = ExerciseSortingOrder.ASC;
        component.setSortingAttribute(SortingAttribute.RELEASE_DATE);
        checkUpcomingExercises();
    });

    it('should filter exercises based on search query', () => {
        const searchInput = fixture.debugElement.query(By.css('#exercise-search-input')).nativeElement;
        searchInput.value = 'pat';
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        const searchButton = fixture.debugElement.query(By.css('#exercise-search-button')).nativeElement;
        const event = new Event('click');
        const exercise1 = new ModelingExercise(UMLDiagramType.ActivityDiagram, undefined, undefined);
        exercise1.title = 'Patten in Software Engineering';
        const exercise2 = new ModelingExercise(UMLDiagramType.ActivityDiagram, undefined, undefined);
        exercise2.title = 'Patten in Software Engineering II';
        const exercise3 = new ModelingExercise(UMLDiagramType.ActivityDiagram, undefined, undefined);
        exercise3.title = 'Introduction to Software Engineering';
        component.course!.exercises = [exercise1, exercise2, exercise3];
        searchButton.dispatchEvent(event);
        expect(component.weeklyExercisesGrouped['noDate'].exercises).toEqual([exercise1, exercise2]);
    });
});
