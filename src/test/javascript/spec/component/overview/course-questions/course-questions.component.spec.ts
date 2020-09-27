import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { CourseQuestionsComponent } from 'app/course/course-questions/course-questions.component';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseQuestionsComponent', () => {
    let component: CourseQuestionsComponent;
    let componentFixture: ComponentFixture<CourseQuestionsComponent>;

    const user1 = {
        id: 1,
    } as User;

    const user2 = {
        id: 2,
    } as User;

    const unApprovedStudentQuestionAnswer = {
        id: 1,
        answerDate: null,
        answerText: 'not approved',
        tutorApproved: false,
        author: user1,
    } as StudentQuestionAnswer;

    const approvedStudentQuestionAnswer = {
        id: 2,
        answerDate: null,
        answerText: 'approved',
        tutorApproved: true,
        author: user2,
    } as StudentQuestionAnswer;

    const studentQuestion = {
        id: 1,
        questionText: 'question',
        creationDate: null,
        answers: [unApprovedStudentQuestionAnswer, approvedStudentQuestionAnswer],
    } as StudentQuestion;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [CourseQuestionsComponent],
        })
            .overrideTemplate(CourseQuestionsComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseQuestionsComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should count approved answers correctly', () => {
        component.studentQuestions = [studentQuestion];
        componentFixture.detectChanges();
        expect(component.getNumberOfApprovedAnswers(studentQuestion)).to.deep.equal(1);
    });
});
