import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { StudentQuestionsComponent } from 'app/overview/student-questions/student-questions.component';
import { Lecture } from 'app/entities/lecture.model';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { Course } from 'app/entities/course.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentQuestionRowComponent', () => {
    let component: StudentQuestionsComponent;
    let componentFixture: ComponentFixture<StudentQuestionsComponent>;

    const course = {
        id: 1,
    } as Course;

    const unApprovedStudentQuestionAnswer = {
        id: 1,
        answerDate: undefined,
        answerText: 'not approved',
        tutorApproved: false,
    } as StudentQuestionAnswer;

    const approvedStudentQuestionAnswer = {
        id: 2,
        answerDate: undefined,
        answerText: 'approved',
        tutorApproved: true,
    } as StudentQuestionAnswer;

    const studentQuestion1 = {
        id: 1,
        creationDate: undefined,
        answers: [unApprovedStudentQuestionAnswer, approvedStudentQuestionAnswer],
    } as StudentQuestion;

    const studentQuestion2 = {
        id: 2,
        creationDate: undefined,
        answers: [unApprovedStudentQuestionAnswer, approvedStudentQuestionAnswer],
    } as StudentQuestion;

    const lectureDefault = {
        id: 1,
        title: 'test',
        description: 'test',
        startDate: undefined,
        endDate: undefined,
        studentQuestions: [studentQuestion1, studentQuestion2],
        isAtLeastInstructor: true,
        course,
    } as Lecture;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
            ],
            declarations: [StudentQuestionsComponent],
        })
            .overrideTemplate(StudentQuestionsComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StudentQuestionsComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should set student questions correctly', () => {
        component.lecture = lectureDefault;
        component.ngOnInit();
        expect(component.studentQuestions).to.deep.equal([studentQuestion1, studentQuestion2]);
    });

    it('should delete studentQuestion from list', () => {
        component.lecture = lectureDefault;
        component.ngOnInit();
        component.deleteQuestionFromList(studentQuestion1);
        expect(component.studentQuestions).to.deep.equal([studentQuestion2]);
    });
});
