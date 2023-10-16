import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExercisesGroupedByWeekComponent } from 'app/overview/course-exercises/course-exercises-grouped-by-week.component';
import * as utils from 'app/shared/util/utils';
import { ExerciseFilter, ExerciseWithDueDate } from 'app/overview/course-exercises/course-exercises.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';

const currentExercise_1 = {
    id: 3,
    dueDate: dayjs().add(2, 'days'),
} as TextExercise;

describe('CourseExercisesGroupedByWeekComponent', () => {
    let fixture: ComponentFixture<CourseExercisesGroupedByWeekComponent>;
    let component: CourseExercisesGroupedByWeekComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExercisesGroupedByWeekComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockComponent(CourseExerciseRowComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExercisesGroupedByWeekComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const getAsMutableObjectSpy = jest.spyOn(utils, 'getAsMutableObject');

        component.ngOnInit();
        component.ngOnChanges();
        expect(component).toBeTruthy();
        expect(getAsMutableObjectSpy).toHaveBeenCalledTimes(2);
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
    });
});
