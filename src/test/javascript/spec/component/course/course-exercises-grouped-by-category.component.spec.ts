import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockPipe } from 'ng-mocks';
// import dayjs from 'dayjs/esm';
// import {TextExercise} from 'app/entities/text-exercise.model';
import { CourseExercisesGroupedByCategoryComponent } from 'app/overview/course-exercises/course-exercises-grouped-by-category.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

// const pastExercise_1 = {
//     id: 1,
//     dueDate: dayjs().subtract(2, 'days')
// } as TextExercise
// const pastExercise_2 = {
//     id: 2,
//     dueDate: dayjs().subtract(2, 'hours')
// } as TextExercise
//
// const currentExercise_1 = {
//     id: 3,
//     dueDate: dayjs().add(2, 'days')
// } as TextExercise
// const currentExercise_2 = {
//     id: 4,
//     dueDate: dayjs().add(2, 'hours')
// } as TextExercise
//
// const futureExercise_1 = {
//     id: 5,
//     dueDate: dayjs().add(8, 'days')
// } as TextExercise
// const futureExercise_2 = {
//     id: 6,
//     dueDate: dayjs().add(7, 'hours').add(2, 'hours')
// } as TextExercise
//
// const noDueDateExercise_1 = {
//     id: 7,
// } as TextExercise
// const noDueDateExercise_2 = {
//     id: 8
// } as TextExercise

// const filteredExercises: Exercise[] = [pastExercise_1, pastExercise_2, currentExercise_1, currentExercise_2, futureExercise_1, futureExercise_2, noDueDateExercise_1, noDueDateExercise_2]

describe('CourseExercisesGroupedByCategoryComponent', () => {
    let fixture: ComponentFixture<CourseExercisesGroupedByCategoryComponent>;
    let component: CourseExercisesGroupedByCategoryComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExercisesGroupedByCategoryComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExercisesGroupedByCategoryComponent);
                component = fixture.componentInstance;
                // courseStorageService = TestBed.inject(CourseStorageService);
                // exerciseService = TestBed.inject(ExerciseService);
                // localStorageService = TestBed.inject(LocalStorageService);
                //
                // course = new Course();
                // course.id = 123;
                // exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
                // exercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(1, 'days');
                // exercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(1, 'days');
                // course.exercises = [exercise];
                // course.unenrollmentEnabled = true;
                // course.unenrollmentEndDate = dayjs().add(1, 'days');
                // jest.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of(course));
                // jest.spyOn(localStorageService, 'retrieve').mockReturnValue('OVERDUE,NEEDS_WORK');
                // courseStorageStub = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
                //
                // modalService = fixture.debugElement.injector.get(NgbModal);
                // openModalStub = jest.spyOn(modalService, 'open');
                // component.filteredExercises = filteredExercises

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        // expect(component.course).toEqual(course);
        // expect(courseStorageStub.mock.calls).toHaveLength(1);
        // expect(courseStorageStub.mock.calls[0][0]).toBe(course.id);
    });

    describe('groupExercisesByDueDate', () => {
        it('should handle if no exercises are present', () => {
            component.filteredExercises = undefined;

            fixture.detectChanges();

            // @ts-ignore
            expect(component.exerciseGroups).toEqual(component.DEFAULT_EXERCISE_GROUPS);
        });
        it('should assign exercises to correct groups', () => {
            //     //@ts-ignore spying on private method
            //     const groupExerciseByDueDateSpy = jest.spyOn(component, 'groupExercisesByDueDate')
            //
            //     component.filteredExercises = filteredExercises
            //     fixture.detectChanges()
            //
            //     expect(groupExerciseByDueDateSpy).toHaveBeenCalled()
            //
        });
    });
});
