import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/feedback/feedback-modal.component';
import { FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { provideTranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

describe('FeedbackModalComponent', () => {
    let fixture: ComponentFixture<FeedbackModalComponent>;
    let component: FeedbackModalComponent;
    let http: HttpTestingController;

    const mockFeedbackDetail: FeedbackDetail = {
        feedbackIds: [1, 2, 3, 4, 5],
        count: 5,
        relativeCount: 25.0,
        detailTexts: ['Some feedback detail'],
        testCaseName: 'testCase1',
        taskName: '1',
        errorCategory: 'StudentError',
        hasLongFeedbackText: true,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackModalComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        }).compileComponents();
        fixture = TestBed.createComponent(FeedbackModalComponent);
        component = fixture.componentInstance;
        http = TestBed.inject(HttpTestingController);
        fixture.componentRef.setInput('feedbackDetail', mockFeedbackDetail);
        fixture.detectChanges();
    });

    afterEach(() => {
        http.verify();
        vi.restoreAllMocks();
    });

    it('should load long feedback text of the first feedback as plain text', () => {
        const request = http.expectOne('/api/assessment/feedbacks/1/long-feedback');
        expect(request.request.responseType).toBe('text');
        request.flush('Loaded long feedback');
        expect(component.longFeedbackText()).toBe('Loaded long feedback');
    });

    it('should fall back to the first detail text when the long feedback is empty', () => {
        http.expectOne('/api/assessment/feedbacks/1/long-feedback').flush('');
        expect(component.longFeedbackText()).toBe('Some feedback detail');
    });

    it('should initialize with the provided feedback detail', () => {
        http.expectOne('/api/assessment/feedbacks/1/long-feedback').flush('Loaded long feedback');
        expect(component.feedbackDetail()).toEqual(mockFeedbackDetail);
        expect(component.feedbackDetail().detailTexts).toStrictEqual(['Some feedback detail']);
        expect(component.feedbackDetail().testCaseName).toBe('testCase1');
    });

    it('should emit closed when the dialog is closed', () => {
        http.expectOne('/api/assessment/feedbacks/1/long-feedback').flush('Loaded long feedback');
        const closedSpy = vi.fn();
        component.closed.subscribe(closedSpy);
        (document.querySelector('[role="dialog"] button[aria-label]') as HTMLButtonElement).click();
        expect(closedSpy).toHaveBeenCalledOnce();
    });
});
