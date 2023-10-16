import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { TextExercise } from 'app/entities/text-exercise.model';
import { CourseExercisesGroupedByCategoryComponent } from 'app/overview/course-exercises/course-exercises-grouped-by-category.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Exercise } from 'app/entities/exercise.model';
import { cloneDeep } from 'lodash-es';

const pastExercise_1 = {
    id: 1,
    dueDate: dayjs().subtract(2, 'days'),
} as TextExercise;
const pastExercise_2 = {
    id: 2,
    dueDate: dayjs().subtract(2, 'hours'),
} as TextExercise;

const currentExercise_1 = {
    id: 3,
    dueDate: dayjs().add(2, 'days'),
} as TextExercise;
const currentExercise_2 = {
    id: 4,
    dueDate: dayjs().add(2, 'hours'),
} as TextExercise;

const futureExercise_1 = {
    id: 5,
    dueDate: dayjs().add(8, 'days'),
} as TextExercise;
const futureExercise_2 = {
    id: 6,
    dueDate: dayjs().add(7, 'days').add(2, 'hours'),
} as TextExercise;

const noDueDateExercise_1 = {
    id: 7,
} as TextExercise;
const noDueDateExercise_2 = {
    id: 8,
} as TextExercise;

const filteredExercises: Exercise[] = [
    pastExercise_1,
    pastExercise_2,
    currentExercise_1,
    currentExercise_2,
    futureExercise_1,
    futureExercise_2,
    noDueDateExercise_1,
    noDueDateExercise_2,
];

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

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    describe('groupExercisesByDueDate', () => {
        it('should assign default groups if no exercises are present', () => {
            component.filteredExercises = undefined;
            const defaultExerciseGroups = component.getDefaultExerciseGroups();

            component.ngOnChanges();

            expect(component.exerciseGroups).toEqual(defaultExerciseGroups);
        });

        it('should assign exercises to correct groups with correct collapsed state', () => {
            const ngOnChangesSpy = jest.spyOn(component, 'ngOnChanges');
            //@ts-ignore spying on private method
            const groupExerciseByDueDateSpy = jest.spyOn(component, 'groupExercisesByDueDate');
            //@ts-ignore spying on private method
            const adjustExpandedOrCollapsedStateOfExerciseGroupsSpy = jest.spyOn(component, 'adjustExpandedOrCollapsedStateOfExerciseGroups');

            component.filteredExercises = filteredExercises;

            component.ngOnChanges();

            expect(ngOnChangesSpy).toHaveBeenCalledOnce();
            expect(groupExerciseByDueDateSpy).toHaveBeenCalledOnce();
            expect(adjustExpandedOrCollapsedStateOfExerciseGroupsSpy).toHaveBeenCalledOnce();
            expect(component.exerciseGroups.past.exercises).toEqual([pastExercise_1, pastExercise_2]);
            expect(component.exerciseGroups.current.exercises).toEqual([currentExercise_1, currentExercise_2]);
            expect(component.exerciseGroups.future.exercises).toEqual([futureExercise_1, futureExercise_2]);
            expect(component.exerciseGroups.noDueDate.exercises).toEqual([noDueDateExercise_1, noDueDateExercise_2]);

            expect(component.exerciseGroups.past.isCollapsed).toBeTrue();
            expect(component.exerciseGroups.current.isCollapsed).toBeFalse();
            expect(component.exerciseGroups.future.isCollapsed).toBeFalse();
            expect(component.exerciseGroups.noDueDate.isCollapsed).toBeTrue();
        });
    });

    describe('adjustExpandedOrCollapsedStateOfExerciseGroups', () => {
        it('should never expand past section by default', () => {
            component.filteredExercises = [pastExercise_1];

            component.ngOnChanges();

            expect(component.exerciseGroups.past.isCollapsed).toBeTrue();
        });

        it('should expand noDueDate section', () => {
            component.filteredExercises = [pastExercise_1, noDueDateExercise_1];

            component.ngOnChanges();

            expect(component.exerciseGroups.past.isCollapsed).toBeTrue();
            expect(component.exerciseGroups.noDueDate.isCollapsed).toBeFalse();
        });

        it('should expand future section', () => {
            component.filteredExercises = [pastExercise_1, futureExercise_1, noDueDateExercise_1];

            component.ngOnChanges();

            expect(component.exerciseGroups.past.isCollapsed).toBeTrue();
            expect(component.exerciseGroups.future.isCollapsed).toBeFalse();
            expect(component.exerciseGroups.noDueDate.isCollapsed).toBeTrue();
        });

        it('should expand current section', () => {
            component.filteredExercises = [pastExercise_1, currentExercise_1, noDueDateExercise_1];

            component.ngOnChanges();

            expect(component.exerciseGroups.past.isCollapsed).toBeTrue();
            expect(component.exerciseGroups.current.isCollapsed).toBeFalse();
            expect(component.exerciseGroups.noDueDate.isCollapsed).toBeTrue();
        });

        it('should expand current and future section', () => {
            component.filteredExercises = [pastExercise_1, currentExercise_1, futureExercise_1, noDueDateExercise_1];

            component.ngOnChanges();

            expect(component.exerciseGroups.past.isCollapsed).toBeTrue();
            expect(component.exerciseGroups.current.isCollapsed).toBeFalse();
            expect(component.exerciseGroups.future.isCollapsed).toBeFalse();
            expect(component.exerciseGroups.noDueDate.isCollapsed).toBeTrue();
        });

        describe('should handle search', () => {
            it('by expanding all exerciseGroups', () => {
                //@ts-ignore spying on private method
                const expandAllExercisesAndSaveStateBeforeSearchSpy = jest.spyOn(component, 'expandAllExercisesAndSaveStateBeforeSearch');

                component.filteredExercises = filteredExercises;
                component.appliedSearchString = 'Text Exercise Title';

                component.ngOnChanges();

                expect(expandAllExercisesAndSaveStateBeforeSearchSpy).toHaveBeenCalledOnce();

                expectAllExerciseGroupsAreExpanded();
            });

            it('by saving collapsed states from before search', () => {
                //@ts-ignore spying on private method
                const expandAllExercisesAndSaveStateBeforeSearchSpy = jest.spyOn(component, 'expandAllExercisesAndSaveStateBeforeSearch');

                component.filteredExercises = filteredExercises;
                component.ngOnChanges();
                component.exerciseGroups.past.isCollapsed = false;
                component.exerciseGroups.current.isCollapsed = true;
                component.exerciseGroups.future.isCollapsed = true;
                component.exerciseGroups.noDueDate.isCollapsed = true;

                const exerciseGroupsBeforeSearch = cloneDeep(component.exerciseGroups);
                component.appliedSearchString = 'Text Exercise Title';

                component.ngOnChanges();

                expect(expandAllExercisesAndSaveStateBeforeSearchSpy).toHaveBeenCalledOnce();
                expect(component.exerciseGroupsBeforeSearch).toEqual(exerciseGroupsBeforeSearch);
                expectAllExerciseGroupsAreExpanded();

                // properly resetting after search is over
                component.appliedSearchString = '';
                component.ngOnChanges();
                expect(component.exerciseGroups).toEqual(exerciseGroupsBeforeSearch);
            });
        });

        it('should keep current collapsed states if filter is applied', () => {
            //@ts-ignore spying on private method
            const keepCurrentCollapsedOrExpandedStateOfExerciseGroupsSpy = jest.spyOn(component, 'keepCurrentCollapsedOrExpandedStateOfExerciseGroups');

            component.filteredExercises = [pastExercise_1, pastExercise_2, currentExercise_1, currentExercise_2, futureExercise_1, noDueDateExercise_1];
            component.appliedSearchString = '';
            component.ngOnChanges();
            component.exerciseGroups.past.isCollapsed = false;
            component.exerciseGroups.current.isCollapsed = true;
            component.exerciseGroups.future.isCollapsed = true;
            component.exerciseGroups.noDueDate.isCollapsed = false;

            const exercisesWithNewAppliedFilter = [pastExercise_1, currentExercise_2, futureExercise_1];
            component.filteredExercises = exercisesWithNewAppliedFilter;

            component.ngOnChanges();
            expect(keepCurrentCollapsedOrExpandedStateOfExerciseGroupsSpy).toHaveBeenCalledTimes(2);
            expect(component.exerciseGroups.past.isCollapsed).toBeFalse();
            expect(component.exerciseGroups.current.isCollapsed).toBeTrue();
            expect(component.exerciseGroups.future.isCollapsed).toBeTrue();
            expect(component.exerciseGroups.noDueDate.isCollapsed).toBeFalse();
        });
    });

    function expectAllExerciseGroupsAreExpanded() {
        expect(component.exerciseGroups.past.isCollapsed).toBeFalse();
        expect(component.exerciseGroups.current.isCollapsed).toBeFalse();
        expect(component.exerciseGroups.future.isCollapsed).toBeFalse();
        expect(component.exerciseGroups.noDueDate.isCollapsed).toBeFalse();
    }
});
