import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExerciseInfoComponent } from 'app/exercises/shared/exercise-info/exercise-info.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';

describe('Exercise Info Component', () => {
    let fixture: ComponentFixture<ExerciseInfoComponent>;
    let comp: ExerciseInfoComponent;

    const dateOne = dayjs();
    const dateTwo = dateOne.add(1, 'day');
    const dateWeek = dateOne.add(7, 'days');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseInfoComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseInfoComponent);
                comp = fixture.componentInstance;
            });
    });

    it.each([
        [{}, undefined, undefined],
        [{ dueDate: dateOne } as Exercise, undefined, dateOne],
        [{ dueDate: dateOne } as Exercise, {}, dateOne],
        [{ dueDate: dateOne } as Exercise, { individualDueDate: dateTwo }, dateTwo],
    ])('should determine due date', (exercise: Exercise, studentParticipation: StudentParticipation | undefined, expectedDueDate: dayjs.Dayjs) => {
        comp.exercise = exercise;
        comp.studentParticipation = studentParticipation;

        comp.ngOnInit();

        expect(comp.dueDate).toEqual(expectedDueDate);
    });

    it.each([
        [{ course: { maxComplaintTimeDays: 7 } } as Exercise, undefined, undefined, false],
        [{ course: { maxComplaintTimeDays: 7 }, dueDate: dateOne } as Exercise, undefined, undefined, false],
        [{ course: { maxComplaintTimeDays: 7 }, dueDate: dateOne } as Exercise, { submissionCount: 42 } as StudentParticipation, undefined, true],
        [{ course: { maxComplaintTimeDays: 7 }, dueDate: dateOne } as Exercise, { results: [{ completionDate: dateOne, rated: true }] } as StudentParticipation, dateWeek, false],
    ])(
        'should determine complaint state',
        (exercise: Exercise, studentParticipation: StudentParticipation | undefined, expectedComplaintDate: dayjs.Dayjs | undefined, canComplainLaterOn: boolean) => {
            comp.exercise = exercise;
            comp.studentParticipation = studentParticipation;

            comp.ngOnInit();

            expect(comp.individualComplaintDueDate).toEqual(expectedComplaintDate);
            expect(comp.canComplainLaterOn).toBe(canComplainLaterOn);
        },
    );
});
