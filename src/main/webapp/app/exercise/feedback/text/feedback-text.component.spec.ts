import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FeedbackTextComponent } from 'app/exercise/feedback/text/feedback-text.component';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { provideTranslateService } from '@ngx-translate/core';

describe('FeedbackTextComponent', () => {
    let fixture: ComponentFixture<FeedbackTextComponent>;
    let comp: FeedbackTextComponent;

    let http: HttpTestingController;

    const longFeedbackUrl = (feedbackId: number) => `/api/assessment/feedbacks/${feedbackId}/long-feedback`;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackTextComponent);
        comp = fixture.componentInstance;

        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        http.verify();
        vi.restoreAllMocks();
    });

    it('should set an empty text if there is no feedback text', () => {
        const text = undefined;
        fixture.componentRef.setInput('feedback', getFeedbackItem(text, getFeedbackReference(1, 2, false)));
        fixture.detectChanges();

        expect(comp.text()).toBe('');
    });

    it('should set the text to the feedback text', () => {
        const text = 'dummy text';
        fixture.componentRef.setInput('feedback', getFeedbackItem(text, getFeedbackReference(1, 2, false)));
        fixture.detectChanges();

        expect(comp.text()).toBe(text);
    });

    it('should not fetch long feedback if it does not exist', async () => {
        fixture.componentRef.setInput('feedback', getFeedbackItem('', getFeedbackReference(1, 2, false)));
        fixture.detectChanges();
        await fixture.whenStable();

        http.expectNone(longFeedbackUrl(2));
    });

    it('should fetch long feedback', async () => {
        const longFeedbackText: string = 'long feedback text';

        fixture.componentRef.setInput('feedback', getFeedbackItem('', getFeedbackReference(1, 2, true)));
        fixture.detectChanges();
        const request = http.expectOne(longFeedbackUrl(2));
        expect(request.request.method).toBe('GET');
        expect(request.request.responseType).toBe('text');
        request.flush(longFeedbackText);
        await fixture.whenStable();

        expect(comp.text()).toBe(longFeedbackText);
        expect(comp.downloadText()).toBeDefined();
        expect(comp.downloadFilename()).toBeDefined();
    });

    it('should create a download link for very long feedback', async () => {
        const longFeedbackText = '0'.repeat(100_000);

        fixture.componentRef.setInput('feedback', getFeedbackItem('short version', getFeedbackReference(1, 2, true)));
        fixture.detectChanges();
        http.expectOne(longFeedbackUrl(2)).flush(longFeedbackText);
        await fixture.whenStable();

        expect(comp.text()).toBe(longFeedbackText);
        expect(comp.downloadFilename()).toBe('feedback_2.txt');
        expect(comp.downloadText()).toContain('data:text/plain;charset=utf-8,');
        expect(comp.downloadText()).toContain(longFeedbackText);
    });

    const getFeedbackReference = (resultId: number, feedbackId: number, hasLongFeedbackText: boolean): Feedback => {
        return {
            id: feedbackId,
            result: { id: resultId } as Result,
            hasLongFeedbackText,
        } as Feedback;
    };

    const getFeedbackItem = (text: string | undefined, feedbackReference: Feedback): FeedbackItem => {
        return {
            credits: undefined,
            name: 'ignored',
            type: 'Test',
            text,
            feedbackReference,
        };
    };
});
