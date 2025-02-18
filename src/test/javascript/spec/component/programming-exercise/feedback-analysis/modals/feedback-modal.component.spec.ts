import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/modal/feedback-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { TranslateModule } from '@ngx-translate/core';
import { LongFeedbackTextService } from 'app/exercises/shared/feedback/long-feedback-text.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('FeedbackModalComponent', () => {
    let fixture: ComponentFixture<FeedbackModalComponent>;
    let component: FeedbackModalComponent;
    let activeModal: NgbActiveModal;

    const mockLongFeedbackTextService = {
        find: jest.fn().mockReturnValue({
            subscribe: (callback: (response: { body: string }) => void) => callback({ body: 'Loaded long feedback' }),
        }),
    };

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
            imports: [TranslateModule.forRoot(), FeedbackModalComponent],
            providers: [NgbActiveModal, { provide: LongFeedbackTextService, useValue: mockLongFeedbackTextService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();
        fixture = TestBed.createComponent(FeedbackModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.componentRef.setInput('feedbackDetail', mockFeedbackDetail);
        fixture.detectChanges();
    });

    it('should load long feedback text if hasLongFeedbackText is true', () => {
        expect(mockLongFeedbackTextService.find).toHaveBeenCalledWith(1);
        expect(component.longFeedbackText()).toBe('Loaded long feedback');
    });

    it('should initialize with the provided feedback detail', () => {
        expect(component.feedbackDetail()).toEqual(mockFeedbackDetail);
        expect(component.feedbackDetail().detailTexts).toStrictEqual(['Some feedback detail']);
        expect(component.feedbackDetail().testCaseName).toBe('testCase1');
    });

    it('should call close on activeModal when close is triggered', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.activeModal.close();
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
