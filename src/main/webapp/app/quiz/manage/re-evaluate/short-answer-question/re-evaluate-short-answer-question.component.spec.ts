import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockBuilder, MockInstance, MockProvider } from 'ng-mocks';

import { ReEvaluateShortAnswerQuestionComponent } from 'app/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';
import { ElementRef, signal } from '@angular/core';
import { ShortAnswerQuestionEditComponent } from 'app/quiz/manage/short-answer-question/short-answer-question-edit.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';

describe('ReEvaluateShortAnswerQuestionComponent', () => {
    let fixture: ComponentFixture<ReEvaluateShortAnswerQuestionComponent>;
    let component: ReEvaluateShortAnswerQuestionComponent;

    beforeEach(async () => {
        await MockBuilder(ReEvaluateShortAnswerQuestionComponent).provide(MockProvider(TranslateService));
        // @ts-ignore
        MockInstance(ShortAnswerQuestionEditComponent, 'questionEditor', signal({} as MarkdownEditorMonacoComponent));
        MockInstance(ShortAnswerQuestionEditComponent, 'questionElement', signal({} as ElementRef));
        fixture = TestBed.createComponent(ReEvaluateShortAnswerQuestionComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.componentRef.setInput('question', new ShortAnswerQuestion());
        fixture.componentRef.setInput('questionIndex', 1);
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
});
