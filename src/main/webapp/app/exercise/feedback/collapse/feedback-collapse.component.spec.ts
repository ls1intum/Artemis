import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FeedbackCollapseComponent } from 'app/exercise/feedback/collapse/feedback-collapse.component';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('FeedbackCollapseComponent', () => {
    setupTestBed({ zoneless: true });

    /*
     * Same value as in feedback-collapse.component.ts
     */
    const FEEDBACK_PREVIEW_CHARACTER_LIMIT = 300;
    let component: FeedbackCollapseComponent;
    let fixture: ComponentFixture<FeedbackCollapseComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackCollapseComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, TranslateStore],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackCollapseComponent);
        component = fixture.componentInstance;
    });

    it('should not truncate if not necessary', () => {
        fixture.componentRef.setInput('feedback', getFeedbackItem('a'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT - 1)));
        fixture.changeDetectorRef.detectChanges();

        expect(component.previewText).toBeUndefined();
    });

    it('should truncate if necessary', () => {
        const text = '0123456789'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT);
        fixture.componentRef.setInput('feedback', getFeedbackItem(text));
        fixture.changeDetectorRef.detectChanges();

        const expected = text.slice(0, FEEDBACK_PREVIEW_CHARACTER_LIMIT);

        expect(component.previewText).toBe(expected);
    });

    it('should only show first line if truncated', () => {
        const text = '0123456789\n'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT);
        fixture.componentRef.setInput('feedback', getFeedbackItem(text));
        fixture.changeDetectorRef.detectChanges();

        const expected = text.slice(0, text.indexOf('\n'));

        expect(component.previewText).toBe(expected);
    });

    it('should only show the first line of feedback if truncating necessary', () => {
        fixture.componentRef.setInput('feedback', getFeedbackItem('Multi\nLine\nText' + 'a'.repeat(300)));
        fixture.changeDetectorRef.detectChanges();

        expect(component.previewText).toBe('Multi');
    });

    it('should always set the preview text if the feedback has long feedback', () => {
        fixture.componentRef.setInput('feedback', getFeedbackItem('Truncated text [...]', true));
        fixture.changeDetectorRef.detectChanges();

        expect(component.previewText).toBe('Truncated text [...]');
    });

    it('should toggle properly', () => {
        fixture.componentRef.setInput('feedback', getFeedbackItem('some text'));
        fixture.changeDetectorRef.detectChanges();

        component.toggleCollapse();
        expect(component.isCollapsed).toBe(false);
        component.toggleCollapse();
        expect(component.isCollapsed).toBe(true);
    });

    const getFeedbackItem = (text: string, hasLongFeedbackText = false): FeedbackItem => {
        return {
            credits: undefined,
            name: 'ignored',
            type: 'Test',
            text,
            feedbackReference: {
                id: 1,
                result: { id: 2 },
                hasLongFeedbackText,
            },
        };
    };
});
