import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ExamParticipationCoverComponent } from 'app/exam/participate/exam-cover/exam-participation-cover.component';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { AlertComponent } from 'app/shared/alert/alert.component.ts';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import * as chai from 'chai';
import { JhiAlertService, JhiTranslateDirective } from 'ng-jhipster';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { TranslatePipeMock } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
chai.use(sinonChai);
const expect = chai.expect;

describe('SomeComponent', () => {
    let fixture: ComponentFixture<ExamParticipationComponent>;
    let component: ExamParticipationComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ExamParticipationComponent,
                TranslatePipeMock,
                MockDirective(JhiTranslateDirective),
                MockComponent(ExamParticipationCoverComponent),
                MockComponent(ExamNavigationBarComponent),
                MockComponent(QuizExamSubmissionComponent),
                MockComponent(TextExamSubmissionComponent),
                MockComponent(ModelingExamSubmissionComponent),
                MockComponent(ProgrammingExamSubmissionComponent),
                MockComponent(JhiConnectionStatusComponent),
                MockComponent(AlertComponent),
                TestRunRibbonComponent,
                MockComponent(ExamParticipationSummaryComponent),
            ],
            providers: [
                MockProvider(JhiWebsocketService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ courseId: '1', examId: '2', testRunId: '3' }),
                    },
                },
                MockProvider(ExamParticipationService),
                MockProvider(ModelingSubmissionService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(TextSubmissionService),
                MockProvider(FileUploadSubmissionService),
                MockProvider(ArtemisServerDateService),
                MockProvider(TranslateService),
                MockProvider(JhiAlertService),
                MockProvider(CourseExerciseService),
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamParticipationComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ExamParticipationComponent).to.be.ok;
    });
});
