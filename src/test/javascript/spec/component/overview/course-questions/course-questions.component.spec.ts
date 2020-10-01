import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { CourseQuestionsComponent } from 'app/course/course-questions/course-questions.component';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { StudentQuestionForOverview } from 'app/course/course-questions/course-questions.component';
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
        answerDate: undefined,
        answerText: 'not approved',
        tutorApproved: false,
        author: user1,
    } as StudentQuestionAnswer;

    const approvedStudentQuestionAnswer = {
        id: 2,
        answerDate: undefined,
        answerText: 'approved',
        tutorApproved: true,
        author: user2,
    } as StudentQuestionAnswer;

    const studentQuestion = {
        id: 1,
        questionText: 'question',
        creationDate: undefined,
        answers: [unApprovedStudentQuestionAnswer, approvedStudentQuestionAnswer],
    } as StudentQuestion;

    const studentQuestionForOverview = {
        id: 1,
        questionText: 'question',
        creationDate: undefined,
        votes: 1,
        answers: 2,
        approvedAnswers: 1,
        exerciseOrLectureId: 1,
        exerciseOrLectureTitle: 'Test exercise',
        belongsToExercise: true,
    } as StudentQuestionForOverview;

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
        expect(component.getNumberOfApprovedAnswers(studentQuestion)).to.deep.equal(1);
    });

    it('should hide questions with approved answers', () => {
        component.studentQuestions = [studentQuestionForOverview];
        component.hideQuestionsWithApprovedAnswers();
        expect(component.studentQuestionsToDisplay.length).to.deep.equal(0);
    });

    it('should toggle hiding questions with approved answers', () => {
        component.studentQuestions = [studentQuestionForOverview];
        expect(component.showQuestionsWithApprovedAnswers).to.be.false;
        component.toggleHideQuestions();
        expect(component.showQuestionsWithApprovedAnswers).to.be.true;
        expect(component.studentQuestionsToDisplay.length).to.deep.equal(1);
        component.toggleHideQuestions();
        expect(component.showQuestionsWithApprovedAnswers).to.be.false;
        expect(component.studentQuestionsToDisplay.length).to.deep.equal(0);
    });
});
