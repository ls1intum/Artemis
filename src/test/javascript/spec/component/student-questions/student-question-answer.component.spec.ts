import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { StudentQuestionAnswerComponent } from 'app/overview/student-questions/student-question-answer/student-question-answer.component';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentQuestionAnswerComponent', () => {
    let component: StudentQuestionAnswerComponent;
    let componentFixture: ComponentFixture<StudentQuestionAnswerComponent>;

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

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [StudentQuestionAnswerComponent],
            providers: [{ provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects }],
        })
            .overrideTemplate(StudentQuestionAnswerComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StudentQuestionAnswerComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should be author of answer', () => {
        component.studentQuestionAnswer = approvedStudentQuestionAnswer;
        component.user = user2;
        expect(component.isAuthorOfAnswer(approvedStudentQuestionAnswer)).to.be.true;
    });

    it('should not be author of answer', () => {
        component.studentQuestionAnswer = approvedStudentQuestionAnswer;
        component.user = user2;
        expect(component.isAuthorOfAnswer(unApprovedStudentQuestionAnswer)).to.be.false;
    });

    it('should approve answer', () => {
        component.studentQuestionAnswer = unApprovedStudentQuestionAnswer;
        component.toggleAnswerTutorApproved();
        expect(component.studentQuestionAnswer.tutorApproved).to.be.true;
    });

    it('should unapprove answer', () => {
        component.studentQuestionAnswer = approvedStudentQuestionAnswer;
        component.toggleAnswerTutorApproved();
        expect(component.studentQuestionAnswer.tutorApproved).to.be.false;
    });

    it('should toggle edit mode and reset editor Text', () => {
        component.studentQuestionAnswer = approvedStudentQuestionAnswer;
        component.isEditMode = true;
        component.editText = 'test';
        component.toggleEditMode();
        expect(component.editText).to.deep.equal('approved');
        expect(component.isEditMode).to.be.false;
        component.toggleEditMode();
        expect(component.isEditMode).to.be.true;
    });

    it('should update answerText', () => {
        component.studentQuestionAnswer = approvedStudentQuestionAnswer;
        component.isEditMode = true;
        component.editText = 'test';
        component.saveAnswer();
        expect(component.studentQuestionAnswer.answerText).to.deep.equal('test');
    });
});
