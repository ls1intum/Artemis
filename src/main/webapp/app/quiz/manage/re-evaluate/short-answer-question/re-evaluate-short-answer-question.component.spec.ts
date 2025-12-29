import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

import { ReEvaluateShortAnswerQuestionComponent } from 'app/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ReEvaluateShortAnswerQuestionComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ReEvaluateShortAnswerQuestionComponent>;
    let component: ReEvaluateShortAnswerQuestionComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideTemplate(ReEvaluateShortAnswerQuestionComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ReEvaluateShortAnswerQuestionComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.componentRef.setInput('question', new ShortAnswerQuestion());
        fixture.componentRef.setInput('questionIndex', 1);
        fixture.detectChanges();
        expect(component).toBeDefined();
        expect(component).not.toBeNull();
    });
});
