import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuestionReevaluationActionsComponent } from './question-reevaluation-actions.component';

describe('question reevaluation actions', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }));
    it('keeps the three independently addressable reorder and reset actions', () => {
        const fixture = TestBed.createComponent(QuestionReevaluationActionsComponent);
        const up = vi.fn();
        const down = vi.fn();
        const reset = vi.fn();
        fixture.componentInstance.moveUp.subscribe(up);
        fixture.componentInstance.moveDown.subscribe(down);
        fixture.componentInstance.resetQuestion.subscribe(reset);
        fixture.detectChanges();
        fixture.nativeElement.querySelector('#move-up-button').click();
        fixture.nativeElement.querySelector('#move-down-button').click();
        fixture.nativeElement.querySelector('#reset-button').click();
        expect(up).toHaveBeenCalledOnce();
        expect(down).toHaveBeenCalledOnce();
        expect(reset).toHaveBeenCalledOnce();
    });
});
