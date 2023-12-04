import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';
import { QuizPoolMappingQuestionListComponent } from 'app/exercises/quiz/manage/quiz-pool-mapping-question-list.component';
import { QuizPoolMappingComponent } from 'app/exercises/quiz/manage/quiz-pool-mapping.component';
import { QuizGroup } from 'app/entities/quiz/quiz-group.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';

describe('QuizPoolMappingComponent', () => {
    let fixture: ComponentFixture<QuizPoolMappingComponent>;
    let component: QuizPoolMappingComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule, MockDirective(NgbTooltip)],
            declarations: [
                QuizPoolMappingComponent,
                ButtonComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                MockComponent(QuizPoolMappingQuestionListComponent),
                MockDirective(NgModel),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizPoolMappingComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should add group', () => {
        expect(component.quizGroups).toBeArrayOfSize(0);

        component.addGroup('Test Group');

        expect(component.quizGroups).toBeArrayOfSize(1);
    });

    it('should not add group with empty name', () => {
        expect(component.quizGroups).toBeArrayOfSize(0);

        component.addGroup('');

        expect(component.quizGroups).toBeArrayOfSize(0);
    });

    it('should not add group with name consisting of more than 100 characters', () => {
        expect(component.quizGroups).toBeArrayOfSize(0);

        component.addGroup('Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean ma');

        expect(component.quizGroups).toBeArrayOfSize(0);
    });

    it('should not add group with the same name', () => {
        expect(component.quizGroups).toBeArrayOfSize(0);

        component.addGroup('Test Group');
        component.addGroup('Test Group');

        expect(component.quizGroups).toBeArrayOfSize(1);
    });

    it('should delete group', () => {
        const quizGroup = new QuizGroup();
        quizGroup.name = 'Group 1';
        const question = new MultipleChoiceQuestion();
        question.quizGroup = quizGroup;
        component.quizGroups = [quizGroup];
        component.quizQuestions = [question];
        const addQuestion = jest.spyOn(component, 'addQuestion').mockImplementation();

        component.handleUpdate();
        component.deleteGroup(0);

        expect(component.quizGroups).toBeArrayOfSize(0);
        expect(addQuestion).toHaveBeenCalledOnce();
        expect(addQuestion).toHaveBeenCalledWith(question);
    });

    it('should add question', () => {
        const question = new MultipleChoiceQuestion();
        component.addQuestion(question);
        expect(component.unmappedQuizQuestions).toBeArrayOfSize(1);
        expect(component.unmappedQuizQuestions[0]).toEqual(question);
    });

    it('should delete unmapped question', () => {
        const question = new MultipleChoiceQuestion();
        component.deleteQuestion(question);
        expect(component.unmappedQuizQuestions).toBeEmpty();
    });

    it('should delete mapped question', () => {
        const quizGroup = new QuizGroup();
        quizGroup.name = 'Test Group';
        const question = new MultipleChoiceQuestion();
        question.quizGroup = quizGroup;
        component.quizGroups = [quizGroup];
        component.handleUpdate();

        component.deleteQuestion(question);

        const questions = component.quizGroupNameQuestionsMap.get(quizGroup.name);
        expect(questions).toBeEmpty();
    });

    it('should return group names that do not have questions', () => {
        component.quizGroupNameQuestionsMap.set('Group 1', []);
        component.quizGroupNameQuestionsMap.set('Group 2', [new MultipleChoiceQuestion()]);
        const groupNamesWithNoQuestion = component.getGroupNamesWithNoQuestion();
        expect(groupNamesWithNoQuestion).toBeArrayOfSize(1);
        expect(groupNamesWithNoQuestion[0]).toBe('Group 1');
    });

    it('should return true if some groups with no questions', () => {
        component.quizGroupNameQuestionsMap.set('Group 1', []);
        component.quizGroupNameQuestionsMap.set('Group 2', [new MultipleChoiceQuestion()]);
        expect(component.hasGroupsWithNoQuestion()).toBeTrue();
    });

    it('should return false if all groups have at least 1 question', () => {
        component.quizGroupNameQuestionsMap.set('Group 1', [new MultipleChoiceQuestion()]);
        component.quizGroupNameQuestionsMap.set('Group 2', [new MultipleChoiceQuestion()]);
        expect(component.hasGroupsWithNoQuestion()).toBeFalse();
    });

    it('should return group names that have questions with different points', () => {
        const question0 = new MultipleChoiceQuestion();
        question0.points = 1;
        const question1 = new MultipleChoiceQuestion();
        question1.points = 1;
        const question2 = new MultipleChoiceQuestion();
        question2.points = 1;
        const question3 = new MultipleChoiceQuestion();
        question3.points = 2;
        component.quizGroupNameQuestionsMap.set('Group 1', [question0, question1]);
        component.quizGroupNameQuestionsMap.set('Group 2', [question2, question3]);
        const groupNamesWithNoQuestion = component.getGroupNamesWithDifferentQuestionPoints();
        expect(groupNamesWithNoQuestion).toBeArrayOfSize(1);
        expect(groupNamesWithNoQuestion[0]).toBe('Group 2');
    });

    it('should return true if some groups have questions with different points', () => {
        const question0 = new MultipleChoiceQuestion();
        question0.points = 1;
        const question1 = new MultipleChoiceQuestion();
        question1.points = 1;
        const question2 = new MultipleChoiceQuestion();
        question2.points = 1;
        const question3 = new MultipleChoiceQuestion();
        question3.points = 2;
        component.quizGroupNameQuestionsMap.set('Group 1', [question0, question1]);
        component.quizGroupNameQuestionsMap.set('Group 2', [question2, question3]);
        expect(component.hasGroupsWithDifferentQuestionPoints()).toBeTrue();
    });

    it('should return false if some groups have questions with different points', () => {
        const question0 = new MultipleChoiceQuestion();
        question0.points = 1;
        const question1 = new MultipleChoiceQuestion();
        question1.points = 1;
        const question2 = new MultipleChoiceQuestion();
        question2.points = 1;
        const question3 = new MultipleChoiceQuestion();
        question3.points = 1;
        component.quizGroupNameQuestionsMap.set('Group 1', [question0, question1]);
        component.quizGroupNameQuestionsMap.set('Group 2', [question2, question3]);
        expect(component.hasGroupsWithDifferentQuestionPoints()).toBeFalse();
    });

    it('should set unmappedQuizQuestions and quizGroupNameQuestionsMap when inputs are changed', () => {
        const quizGroup = new QuizGroup();
        quizGroup.name = 'Test Group';
        const question0 = new MultipleChoiceQuestion();
        const question1 = new MultipleChoiceQuestion();
        question0.quizGroup = quizGroup;
        component.quizGroups = [quizGroup];
        component.quizQuestions = [question0, question1];
        component.ngOnChanges();
        expect(component.unmappedQuizQuestions).toBeArrayOfSize(1);
        expect(component.unmappedQuizQuestions[0]).toEqual(question1);
        expect(component.quizGroupNameQuestionsMap.size).toBe(1);
        const questions = component.quizGroupNameQuestionsMap.get('Test Group');
        expect(questions).toBeArrayOfSize(1);
        expect(questions![0]).toEqual(question0);
    });

    it('should set quiz group to quiz question when question is dropped to the group', () => {
        const quizGroup = new QuizGroup();
        quizGroup.name = 'Test Group';
        const question = new MultipleChoiceQuestion();
        component.handleOnQuizQuestionDropped(question, quizGroup);
        expect(question.quizGroup).toEqual(quizGroup);
    });

    it('should return max points', () => {
        const question0 = new MultipleChoiceQuestion();
        question0.points = 1;
        const question1 = new MultipleChoiceQuestion();
        question1.points = undefined;
        const question2 = new MultipleChoiceQuestion();
        question2.points = 1;
        const question3 = new MultipleChoiceQuestion();
        question3.points = undefined;
        component.quizGroupNameQuestionsMap = new Map();
        component.quizGroupNameQuestionsMap.set('Group 1', [question0, question1]);
        component.quizGroupNameQuestionsMap.set('Group 2', []);
        component.unmappedQuizQuestions = [question2, question3];
        expect(component.getMaxPoints()).toBe(2);
    });
});
