import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { By } from '@angular/platform-browser';
import { ReactiveFormsModule } from '@angular/forms';
import { MockModule } from 'ng-mocks';
import { NgbModule, NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

chai.use(sinonChai);
const expect = chai.expect;

describe('Create Test Run Modal Component', () => {
    let comp: CreateTestRunModalComponent;
    let fixture: ComponentFixture<CreateTestRunModalComponent>;
    let activeModal: NgbActiveModal;

    const course = { id: 1 } as Course;
    const exercise = { id: 1 } as Exercise;
    const exerciseGroup1 = { id: 1, exercises: [exercise] } as ExerciseGroup;
    const exam = { id: 1, course, started: true, startDate: moment(), endDate: moment().add(20, 'seconds'), exerciseGroups: [exerciseGroup1] } as Exam;
    const exerciseGroup2 = { id: 2 } as ExerciseGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgbModule), MockModule(ReactiveFormsModule)],
            declarations: [CreateTestRunModalComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CreateTestRunModalComponent);
                comp = fixture.componentInstance;
                activeModal = TestBed.inject(NgbActiveModal);
                comp.exam = exam;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    describe('OnInit', () => {
        it('should initialise the working time form ', fakeAsync(() => {
            fixture.detectChanges();
            expect(comp).to.be.ok;
            expect(!!comp.workingTimeForm).to.be.true;
        }));
    });

    describe('Ignore Exercise groups', () => {
        it('should ignore exercise groups with no exercises', function () {
            comp.exam.exerciseGroups = [exerciseGroup1, exerciseGroup2];
            fixture.detectChanges();
            expect(comp.exam.exerciseGroups!.length).to.equal(1);
        });
    });

    describe('Exercise Selection', () => {
        it('should highlight the exercise when pressed', fakeAsync(() => {
            fixture.detectChanges();
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            expect(Object.values(comp.testRunConfiguration).length).to.be.equal(1);
        }));
        it('should allow submit when an exercise has been selected for every exercise group', fakeAsync(() => {
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            expect(comp.testRunConfigured).to.be.true;
        }));
    });

    describe('Test Run creation', () => {
        it('should create the test run if the configuration is valid', () => {
            fixture.detectChanges();
            const createTestRunButton = fixture.debugElement.query(By.css('#createTestRunButton'));
            expect(createTestRunButton).to.exist;
            expect(createTestRunButton.nativeElement.disabled).to.be.true;
            // @ts-ignore
            comp.onSelectExercise(exercise, exam.exerciseGroups[0]!);
            fixture.detectChanges();
            expect(comp.testRunConfigured).to.be.true;
            expect(createTestRunButton.nativeElement.disabled).to.be.false;
            createTestRunButton.nativeElement.click();
            expect(activeModal.close).to.have.been.calledOnce;
        });
    });
});
