import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { StudentQuestionRowComponent } from 'app/overview/student-questions/student-question-row/student-question-row.component';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

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
        creationDate: undefined,
        answers: [unApprovedStudentQuestionAnswer, approvedStudentQuestionAnswer],
    } as StudentQuestion;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }],
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

    it('should delete studentQuestionAnswer from list', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.ngOnInit();
        componentFixture.detectChanges();
        component.deleteAnswerFromList(unApprovedStudentQuestionAnswer);
        componentFixture.detectChanges();
        expect(component.studentQuestion.answers).to.deep.equal([approvedStudentQuestionAnswer]);
    });

    it('should add studentQuestionAnswer to list', () => {
        component.studentQuestion = studentQuestion;
        componentFixture.detectChanges();
        component.ngOnInit();
        componentFixture.detectChanges();
        component.studentQuestion.answers = [approvedStudentQuestionAnswer];
        componentFixture.detectChanges();
        component.addAnswerToList(unApprovedStudentQuestionAnswer);
        componentFixture.detectChanges();
        expect(component.studentQuestion.answers).to.deep.equal([approvedStudentQuestionAnswer, unApprovedStudentQuestionAnswer]);
    });
});
