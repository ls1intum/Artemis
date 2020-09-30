import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

describe('Create Test Run Modal Component', () => {
    let comp: CreateTestRunModalComponent;
    let fixture: ComponentFixture<CreateTestRunModalComponent>;

    const course = { id: 1 } as Course;
    const exercise = { id: 1 } as Exercise;
    const exerciseGroup1 = { id: 1, exercises: [exercise] } as ExerciseGroup;
    const exam = { id: 1, course, started: true, startDate: moment(), endDate: moment().add(20, 'seconds'), exerciseGroups: [exerciseGroup1] } as Exam;
    const exerciseGroup2 = { id: 2 } as ExerciseGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CreateTestRunModalComponent],
            providers: [],
        })
            .overrideTemplate(CreateTestRunModalComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(CreateTestRunModalComponent);
        comp = fixture.componentInstance;
    });

    describe('OnInit', () => {
        it('should initialise the working time form ', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            comp.ngOnInit();
            // THEN
            expect(!!comp.workingTimeForm).toBeTruthy();
        }));
    });

    describe('Ignore Exercise groups', () => {
        it('should ignore exercise groups with no exercises', function () {
            comp.exam = exam;
            comp.exam.exerciseGroups = [exerciseGroup1, exerciseGroup2];
            fixture.detectChanges();
            expect(comp.exam.exerciseGroups!.length).toBe(1);
        });
    });

    describe('Exercise Selection', () => {
        it('should highlight the exercise when pressed', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            // THEN
            expect(Object.values(comp.testRunConfiguration).length).toBeGreaterThanOrEqual(1);
        }));
        it('should allow submit when an exercise has been selected for every exercise group', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            // THEN
            expect(comp.testRunConfigured).toBeTruthy();
        }));
    });
});
