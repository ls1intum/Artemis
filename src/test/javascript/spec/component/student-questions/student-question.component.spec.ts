import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { StudentQuestionComponent } from 'app/overview/student-questions/student-question/student-question.component';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { StudentVotesComponent } from 'app/overview/student-questions/student-votes/student-votes.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentQuestionComponent', () => {
    let component: StudentQuestionComponent;
    let componentFixture: ComponentFixture<StudentQuestionComponent>;

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
        author: user1,
        answers: [unApprovedStudentQuestionAnswer, approvedStudentQuestionAnswer],
    } as StudentQuestion;

    const maliciousStudentQuestion = {
        id: 2,
        questionText: '<div style="transform: scaleX(-1)">&gt;:)</div>',
        creationDate: undefined,
        author: user2,
        answers: [],
    } as StudentQuestion;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
            declarations: [
                StudentQuestionComponent,
                MockDirective(MarkdownEditorComponent),
                MockDirective(ConfirmIconComponent),
                MockDirective(StudentVotesComponent),
                MockDirective(NgbTooltip),
                MockPipe(ArtemisDatePipe),
                // Don't mock this since we want to test this pipe, too
                HtmlForMarkdownPipe,
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: {
                                get: () => {
                                    return { courseId: 1 };
                                },
                            },
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StudentQuestionComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should toggle edit mode and reset editor Text', () => {
        component.studentQuestion = studentQuestion;
        component.isEditMode = true;
        component.editText = 'test';
        component.toggleEditMode();
        expect(component.editText).to.deep.equal('question');
        expect(component.isEditMode).to.be.false;
        component.toggleEditMode();
        expect(component.isEditMode).to.be.true;
    });

    it('should update questionText', () => {
        component.studentQuestion = studentQuestion;
        component.isEditMode = true;
        component.editText = 'test';
        component.saveQuestion();
        expect(component.studentQuestion.questionText).to.deep.equal('test');
    });

    it('should not display malicious html in question texts', () => {
        component.studentQuestion = maliciousStudentQuestion;
        componentFixture.detectChanges();

        const text = componentFixture.debugElement.nativeElement.querySelector('#questionText');
        expect(text.innerHTML).to.not.equal(maliciousStudentQuestion.questionText);
        expect(text.innerHTML).to.equal('&gt;:)');
    });
});
