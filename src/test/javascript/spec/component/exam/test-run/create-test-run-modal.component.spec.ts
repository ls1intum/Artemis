import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import * as moment from 'moment';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { By } from '@angular/platform-browser';
import { StudentExam } from 'app/entities/student-exam.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('Create Test Run Modal Component', () => {
    let comp: CreateTestRunModalComponent;
    let fixture: ComponentFixture<CreateTestRunModalComponent>;

    const course = { id: 1 } as Course;
    const exercise = { id: 1, title: 'exampleExercise', type: ExerciseType.TEXT } as Exercise;
    const exerciseGroup1 = { id: 1, exercises: [exercise], title: 'exampleExerciseGroup' } as ExerciseGroup;
    const exam = { id: 1, course, started: true, startDate: moment(), endDate: moment().add(20, 'seconds'), exerciseGroups: [exerciseGroup1] } as Exam;
    const exerciseGroup2 = { id: 2 } as ExerciseGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [CreateTestRunModalComponent],
            providers: [NgbModal, NgbActiveModal, { provide: ArtemisDurationFromSecondsPipe, useClass: ArtemisDurationFromSecondsPipe }],
        }).compileComponents();

        fixture = TestBed.createComponent(CreateTestRunModalComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        sinon.restore();
    });

    describe('OnInit', () => {
        it('should initialise the working time form ', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            comp.ngOnInit();
            // THEN
            expect(!!comp.workingTimeForm).to.be.ok;
        }));
    });

    describe('creating test run workflow', () => {
        it('should create a new test run and close the modal', () => {
            const activeModal = TestBed.inject(NgbActiveModal);
            const closeStub = sinon.stub(activeModal, 'close');
            comp.exam = exam;
            fixture.detectChanges();
            comp.workingTimeForm.controls['minutes'].setValue(30);
            comp.workingTimeForm.controls['seconds'].setValue(0);
            const exerciseRow = fixture.debugElement.query(By.css('#exercise-1')).nativeElement;
            expect(exerciseRow).to.be.ok;
            exerciseRow.click();
            fixture.detectChanges();
            expect(comp.testRunConfiguration[1]).to.deep.equal(exercise);
            expect(comp.exam.exerciseGroups!.length).to.equal(1);
            expect(comp.testRunConfigured).to.equal(true);
            const createTestRunButton = fixture.debugElement.query(By.css('#createTestRunButton')).nativeElement;
            createTestRunButton.click();
            expect(closeStub).to.have.been.called;
            const testRun = closeStub.getCall(0).args[0] as StudentExam;
            expect(testRun).to.be.ok;
            expect(testRun.exam).to.equal(exam);
            expect(testRun.exercises).to.contain(exercise);
            expect(testRun.workingTime).to.equal(1800);
        });
    });

    describe('Ignore Exercise groups', () => {
        it('should ignore exercise groups with no exercises', function () {
            comp.exam = exam;
            comp.exam.exerciseGroups = [exerciseGroup1, exerciseGroup2];
            fixture.detectChanges();
            expect(comp.exam.exerciseGroups!.length).to.equal(1);
        });
    });

    describe('Exercise Selection', () => {
        it('should highlight the exercise when pressed', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            // THEN
            expect(Object.values(comp.testRunConfiguration).length).to.be.above(0);
        }));
        it('should allow submit when an exercise has been selected for every exercise group', fakeAsync(() => {
            comp.exam = exam;
            // WHEN
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            // THEN
            expect(comp.testRunConfigured).to.be.ok;
        }));
    });
});
