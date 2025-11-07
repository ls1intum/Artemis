import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { ReEvaluateMultipleChoiceQuestionComponent } from './re-evaluate-multiple-choice-question.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MultipleChoice', () => {
    let component: ReEvaluateMultipleChoiceQuestionComponent;
    let fixture: ComponentFixture<ReEvaluateMultipleChoiceQuestionComponent>;
    const question: MultipleChoiceQuestion = new MultipleChoiceQuestion();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReEvaluateMultipleChoiceQuestionComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        question.answerOptions = [];

        fixture = TestBed.createComponent(ReEvaluateMultipleChoiceQuestionComponent);
        fixture.componentRef.setInput('question', question);
        fixture.componentRef.setInput('questionIndex', 0);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
