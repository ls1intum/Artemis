import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router, RouterModule } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { HttpResponse } from '@angular/common/http';
import { Exam } from 'app/entities/exam.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { BuildPlanLinkDirective } from 'app/exercises/programming/shared/utils/build-plan-link.directive';
import { ExamExerciseRowButtonsComponent } from 'app/exercises/shared/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import * as moment from 'moment';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MockRouter } from '../../../helpers/mocks/service/mock-route.service';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { FileUploadExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { ProgrammingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('Exam Exercise Row Buttons Component', () => {
    const course = new Course();
    course.id = 456;
    course.isAtLeastInstructor = true;

    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    const dueDateStat = { inTime: 0, late: 0 } as DueDateStat;

    const quizQuestions = [
        {
            id: 1,
            text: 'text1',
        },
        {
            id: 2,
            text: 'text2',
        },
    ];

    const fileExercise = {
        id: 1,
        type: ExerciseType.FILE_UPLOAD,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
    };
    const programmingExercise = {
        id: 2,
        type: ExerciseType.PROGRAMMING,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
    };
    const modelingExercise = {
        id: 3,
        type: ExerciseType.MODELING,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
    };
    const textExercise = {
        id: 4,
        type: ExerciseType.TEXT,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
    };
    const quizExercise = {
        id: 5,
        type: ExerciseType.QUIZ,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
        quizQuestions,
    };

    let comp: ExamExerciseRowButtonsComponent;
    let fixture: ComponentFixture<ExamExerciseRowButtonsComponent>;

    let jhiEventManager: JhiEventManager;
    let router: Router;
    let textExerciseService: TextExerciseService;
    let modelingExerciseService: ModelingExerciseService;
    let fileUploadExerciseService: FileUploadExerciseService;
    let quizExerciseService: QuizExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule, RouterModule],
            declarations: [
                ExerciseGroupsComponent,
                ExamExerciseRowButtonsComponent,
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(BuildPlanLinkDirective),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(NgbTooltip),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FileUploadExerciseGroupCellComponent),
                MockComponent(ModelingExerciseGroupCellComponent),
                MockComponent(ProgrammingExerciseGroupCellComponent),
                MockComponent(QuizExerciseGroupCellComponent),
            ],
            providers: [{ provide: Router, useClass: MockRouter }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamExerciseRowButtonsComponent);
        comp = fixture.componentInstance;

        jhiEventManager = TestBed.inject(JhiEventManager);
        textExerciseService = TestBed.inject(TextExerciseService);
        modelingExerciseService = TestBed.inject(ModelingExerciseService);
        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        quizExerciseService = TestBed.inject(QuizExerciseService);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);

        router = TestBed.inject(Router);
    });

    beforeEach(() => {
        comp.course = course;
        comp.exam = exam;
    });

    afterEach(function () {
        sinon.restore();
    });

    describe('check exam is over', () => {
        it('should check exam is over is true', fakeAsync(() => {
            comp.latestIndividualEndDate = moment().subtract(1, 'days');

            const isExamOver = comp.isExamOver();

            expect(isExamOver).to.be.true;
        }));

        it('should check exam is over is false', fakeAsync(() => {
            comp.latestIndividualEndDate = moment().add(1, 'days');

            const isExamOver = comp.isExamOver();

            expect(isExamOver).to.be.false;
        }));
    });

    describe('check exercise deletion', () => {
        it('should delete textExercise', fakeAsync(() => {
            // setup
            comp.exercise = textExercise;
            const textExerciseServiceDeleteStub = sinon.stub(textExerciseService, 'delete').returns(
                of(
                    new HttpResponse<{}>({ body: [] }),
                ),
            );

            comp.deleteExercise();

            expect(textExerciseServiceDeleteStub).to.have.been.calledOnceWith(textExercise.id);
        }));

        it('should delete modelingExercise', fakeAsync(() => {
            comp.exercise = modelingExercise;
            const modelingExerciseServiceDeleteStub = sinon.stub(modelingExerciseService, 'delete').returns(
                of(
                    new HttpResponse<{}>({ body: [] }),
                ),
            );

            comp.deleteExercise();

            expect(modelingExerciseServiceDeleteStub).to.have.been.calledOnceWith(modelingExercise.id);
        }));

        it('should delete fileExercise', fakeAsync(() => {
            comp.exercise = fileExercise;
            const fileExerciseServiceDeleteStub = sinon.stub(fileUploadExerciseService, 'delete').returns(
                of(
                    new HttpResponse<{}>({ body: [] }),
                ),
            );

            comp.deleteExercise();

            expect(fileExerciseServiceDeleteStub).to.have.been.calledOnceWith(fileExercise.id);
        }));

        it('should delete quizExercise', fakeAsync(() => {
            comp.exercise = quizExercise;
            const quizExerciseServiceDeleteStub = sinon.stub(quizExerciseService, 'delete').returns(
                of(
                    new HttpResponse<{}>({ body: [] }),
                ),
            );

            comp.deleteExercise();

            expect(quizExerciseServiceDeleteStub).to.have.been.calledOnceWith(quizExercise.id);
        }));

        it('should delete programmingExercise', fakeAsync(() => {
            comp.exercise = programmingExercise;
            const programmingExerciseServiceDeleteStub = sinon.stub(programmingExerciseService, 'delete').returns(
                of(
                    new HttpResponse<{}>({ body: [] }),
                ),
            );

            comp.deleteProgrammingExercise({ deleteStudentReposBuildPlans: true, deleteBaseReposBuildPlans: false });

            expect(programmingExerciseServiceDeleteStub).to.have.been.calledOnceWith(programmingExercise.id, true, false);
        }));
    });

    describe('check quiz is being exported', () => {
        it('should export quiz exercise by id', fakeAsync(() => {
            comp.exercise = quizExercise;
            const quizExerciseServiceFindStub = sinon.stub(quizExerciseService, 'find').returns(
                of(
                    new HttpResponse<QuizExercise>({ body: quizExercise }),
                ),
            );
            const quizExerciseServiceExportQuizStub = sinon.stub(quizExerciseService, 'exportQuiz');

            comp.exportQuizById(true);

            expect(quizExerciseServiceFindStub).to.have.been.calledOnceWith(quizExercise.id);
            expect(quizExerciseServiceExportQuizStub).to.have.been.calledOnceWith(quizQuestions, true);
        }));
    });
});
