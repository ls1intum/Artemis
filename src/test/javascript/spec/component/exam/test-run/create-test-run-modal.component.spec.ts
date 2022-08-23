import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { StudentExam } from 'app/entities/student-exam.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';

describe('Create Test Run Modal Component', () => {
    let comp: CreateTestRunModalComponent;
    let fixture: ComponentFixture<CreateTestRunModalComponent>;

    const course = { id: 1 } as Course;
    const exercise = { id: 1, title: 'exampleExercise', type: ExerciseType.TEXT } as Exercise;
    const exerciseGroup1 = { id: 1, exercises: [exercise], title: 'exampleExerciseGroup' } as ExerciseGroup;
    const exam = { id: 1, course, started: true, startDate: dayjs(), endDate: dayjs().add(20, 'seconds'), exerciseGroups: [exerciseGroup1] } as Exam;
    const exerciseGroup2 = { id: 2 } as ExerciseGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [CreateTestRunModalComponent],
            providers: [
                NgbModal,
                NgbActiveModal,
                { provide: ArtemisDurationFromSecondsPipe, useClass: ArtemisDurationFromSecondsPipe },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CreateTestRunModalComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('onInit', () => {
        it('should initialise the working time form', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            comp.ngOnInit();
            // THEN
            expect(!!comp.workingTimeForm).not.toBeNull();
        }));
    });

    describe('creating test run workflow', () => {
        it('should create a new test run and close the modal', () => {
            const activeModal = TestBed.inject(NgbActiveModal);
            const closeStub = jest.spyOn(activeModal, 'close');
            comp.exam = exam;
            fixture.detectChanges();
            comp.workingTimeForm.controls['minutes'].setValue(30);
            comp.workingTimeForm.controls['seconds'].setValue(0);
            const exerciseRow = fixture.debugElement.query(By.css('#exercise-1')).nativeElement;
            expect(exerciseRow).not.toBeNull();
            exerciseRow.click();
            fixture.detectChanges();
            expect(comp.testRunConfiguration[1]).toEqual(exercise);
            expect(comp.exam.exerciseGroups!).toHaveLength(1);
            expect(comp.testRunConfigured).toBeTrue();
            const createTestRunButton = fixture.debugElement.query(By.css('#createTestRunButton')).nativeElement;
            createTestRunButton.click();
            expect(closeStub).toHaveBeenCalledOnce();
            const testRun = closeStub.mock.calls[0][0] as StudentExam;
            expect(testRun).not.toBeNull();
            expect(testRun.exam).toEqual(exam);
            expect(testRun.exercises).toContain(exercise);
            expect(testRun.workingTime).toBe(1800);
        });
    });

    describe('Ignore Exercise groups', () => {
        it('should ignore exercise groups with no exercises', () => {
            comp.exam = exam;
            comp.exam.exerciseGroups = [exerciseGroup1, exerciseGroup2];
            fixture.detectChanges();
            expect(comp.exam.exerciseGroups!).toHaveLength(1);
        });
    });

    describe('Exercise Selection', () => {
        it('should highlight the exercise when pressed', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            // THEN
            expect(Object.values(comp.testRunConfiguration).length).toBeGreaterThan(0);
        }));
        it('should allow submit when an exercise has been selected for every exercise group', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            // THEN
            expect(comp.testRunConfigured).not.toBeNull();
        }));
    });
});
