import * as sinon from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';
import { MockComponent, MockDirective, MockPipe, MockModule } from 'ng-mocks';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { TranslatePipe } from '@ngx-translate/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { ExamPointsSummaryComponent } from 'app/exam/participate/summary/points-summary/exam-points-summary.component';
import { NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { JhiTranslateDirective } from 'ng-jhipster';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ComplaintInteractionsComponent } from 'app/complaints/complaint-interactions.component';
import { ProgrammingExamSummaryComponent } from 'app/exam/participate/summary/exercises/programming-exam-summary/programming-exam-summary.component';
import { FileUploadExamSummaryComponent } from 'app/exam/participate/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { QuizExamSummaryComponent } from 'app/exam/participate/summary/exercises/quiz-exam-summary/quiz-exam-summary.component';
import { ModelingExamSummaryComponent } from 'app/exam/participate/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { TextExamSummaryComponent } from 'app/exam/participate/summary/exercises/text-exam-summary/text-exam-summary.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientModule } from '@angular/common/http';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { User } from 'app/core/user/user.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

let fixture: ComponentFixture<ExamParticipationSummaryComponent>;
let component: ExamParticipationSummaryComponent;

const exam = { id: 1, title: 'Test Exam' } as Exam;

const user = { id: 1, name: 'Test User' } as User;

const studentExam = { id: 1, exam: exam, user: user } as StudentExam;

function sharedSetup(url: string[]) {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), FontAwesomeTestingModule, MockModule(NgbModule), HttpClientModule],
            declarations: [
                ExamParticipationSummaryComponent,
                MockComponent(TestRunRibbonComponent),
                MockComponent(ExamPointsSummaryComponent),
                MockComponent(ExamInformationComponent),
                MockComponent(ResultComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ProgrammingExamSummaryComponent),
                MockComponent(QuizExamSummaryComponent),
                MockComponent(ModelingExamSummaryComponent),
                MockComponent(TextExamSummaryComponent),
                MockComponent(FileUploadExamSummaryComponent),
                MockComponent(ComplaintInteractionsComponent),
                MockDirective(JhiTranslateDirective),
                MockPipe(TranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            url: url,
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamParticipationSummaryComponent);
                component = fixture.componentInstance;
                component.studentExam = studentExam;
            });
    });

    afterEach(() => {
        sinon.restore();
    });
}

describe('ExamParticipationSummaryComponent for TestRuns', () => {
    sharedSetup(['', 'test-runs']);

    it('should initialize and display test run ribbon', function () {
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.isTestRun).to.be.true;
        const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
        expect(testRunRibbon).to.exist;
    });
});

describe('ExamParticipationSummaryComponent', () => {
    sharedSetup(['', '']);

    it('should initialize and not display test run ribbon', function () {
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.isTestRun).to.be.false;
        const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
        expect(testRunRibbon).to.not.exist;
    });

    it('should expand all exercises and call print when Export PDF is clicked', fakeAsync(() => {
        const printWindowStub = sinon.stub(global.window, 'print').returns();
        fixture.detectChanges();
        const exportToPDFButton = fixture.debugElement.query(By.css('#exportToPDFButton'));
        expect(exportToPDFButton).to.exist;
        exportToPDFButton.nativeElement.click();
        expect(component.collapsedExerciseIds).to.be.empty;
        tick();
        sinon.assert.called(printWindowStub);
        printWindowStub.restore();
    }));
});
