import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { TranslateModule } from '@ngx-translate/core';

describe('FeedbackModalComponent', () => {
    let fixture: ComponentFixture<FeedbackModalComponent>;
    let component: FeedbackModalComponent;
    let activeModal: NgbActiveModal;

    const mockFeedbackDetail: FeedbackDetail = {
        count: 5,
        relativeCount: 25.0,
        detailText: 'Some feedback detail',
        testCaseName: 'testCase1',
        taskNumber: '1',
        errorCategory: 'StudentError',
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), FeedbackModalComponent],
            providers: [NgbActiveModal],
        }).compileComponents();
        fixture = TestBed.createComponent(FeedbackModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.componentRef.setInput('feedbackDetail', mockFeedbackDetail);
        fixture.detectChanges();
    });

    it('should initialize with the provided feedback detail', () => {
        expect(component.feedbackDetail()).toEqual(mockFeedbackDetail);
        expect(component.feedbackDetail().detailText).toBe('Some feedback detail');
        expect(component.feedbackDetail().testCaseName).toBe('testCase1');
    });

    it('should call close on activeModal when close is triggered', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.activeModal.close();
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
