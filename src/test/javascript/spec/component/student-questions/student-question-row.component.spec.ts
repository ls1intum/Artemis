import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { StudentQuestionRowComponent } from 'app/overview/student-questions/student-question-row.component';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentQuestionRowComponent', () => {
    let component: StudentQuestionRowComponent;
    let componentFixture: ComponentFixture<StudentQuestionRowComponent>;

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
        creationDate: null,
        answers: [unApprovedStudentQuestionAnswer, approvedStudentQuestionAnswer],
    } as StudentQuestion;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [StudentQuestionRowComponent],
        })
            .overrideTemplate(StudentQuestionRowComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StudentQuestionRowComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should sort in approved and not approved answers', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.ngOnInit();
        componentFixture.detectChanges();
        expect(component.approvedQuestionAnswers).to.deep.equal([approvedStudentQuestionAnswer]);
        expect(component.sortedQuestionAnswers).to.deep.equal([unApprovedStudentQuestionAnswer]);
    });

    it('should toggle answer mode and set question', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.toggleAnswerMode(approvedStudentQuestionAnswer);
        componentFixture.detectChanges();
        expect(component.isAnswerMode).to.be.true;
        expect(component.questionAnswerText).to.deep.equal(approvedStudentQuestionAnswer.answerText);
        expect(component.selectedQuestionAnswer).to.deep.equal(approvedStudentQuestionAnswer);
    });

    it('should toggle answer mode and set question to null', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.toggleAnswerMode(approvedStudentQuestionAnswer);
        componentFixture.detectChanges();
        component.toggleAnswerMode(approvedStudentQuestionAnswer);
        componentFixture.detectChanges();
        expect(component.isAnswerMode).to.be.false;
        expect(component.questionAnswerText).to.deep.equal('');
        expect(component.selectedQuestionAnswer).to.deep.equal(null);
    });

    it('should keep answer mode for different question', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.toggleAnswerMode(approvedStudentQuestionAnswer);
        componentFixture.detectChanges();
        component.toggleAnswerMode(unApprovedStudentQuestionAnswer);
        componentFixture.detectChanges();
        expect(component.isAnswerMode).to.be.true;
        expect(component.questionAnswerText).to.deep.equal(unApprovedStudentQuestionAnswer.answerText);
        expect(component.selectedQuestionAnswer).to.deep.equal(unApprovedStudentQuestionAnswer);
    });

    it('should toggle answer mode for new answer', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.toggleAnswerModeForNewAnswer();
        componentFixture.detectChanges();
        expect(component.isAnswerMode).to.be.true;
        expect(component.questionAnswerText).to.deep.equal('');
        expect(component.selectedQuestionAnswer).to.deep.equal(null);
    });

    it('should toggle question edit mode', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.toggleQuestionEditMode();
        componentFixture.detectChanges();
        expect(component.isEditMode).to.be.true;
    });

    it('should return true for user to be author of answer', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.user = user1;
        componentFixture.detectChanges();
        expect(component.isAuthorOfAnswer(unApprovedStudentQuestionAnswer)).to.be.true;
    });

    it('should return false for user to be author of answer', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.user = user1;
        componentFixture.detectChanges();
        expect(component.isAuthorOfAnswer(approvedStudentQuestionAnswer)).to.be.false;
    });
});
