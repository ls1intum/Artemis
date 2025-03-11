import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockBuilder, MockProvider } from 'ng-mocks';

import { ReEvaluateShortAnswerQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';

describe('ReEvaluateShortAnswerQuestionComponent', () => {
    let fixture: ComponentFixture<ReEvaluateShortAnswerQuestionComponent>;
    let component: ReEvaluateShortAnswerQuestionComponent;

    beforeEach(async () => {
        await MockBuilder(ReEvaluateShortAnswerQuestionComponent).provide(MockProvider(TranslateService));
        fixture = TestBed.createComponent(ReEvaluateShortAnswerQuestionComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
});
