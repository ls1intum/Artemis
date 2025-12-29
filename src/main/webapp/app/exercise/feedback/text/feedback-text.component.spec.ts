import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FeedbackTextComponent } from 'app/exercise/feedback/text/feedback-text.component';
import { LongFeedbackTextService } from 'app/exercise/feedback/services/long-feedback-text.service';
import { MockProvider } from 'ng-mocks';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TranslateModule } from '@ngx-translate/core';

describe('FeedbackTextComponent', () => {
    let fixture: ComponentFixture<FeedbackTextComponent>;
    let comp: FeedbackTextComponent;

    let getLongFeedbackStub: ReturnType<typeof jest.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            providers: [MockProvider(LongFeedbackTextService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FeedbackTextComponent);
                comp = fixture.componentInstance;

                const longFeedbackTextService = TestBed.inject(LongFeedbackTextService);
                getLongFeedbackStub = jest.spyOn(longFeedbackTextService, 'find');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set an empty text if there is no feedback text', () => {
        const text = undefined;
        fixture.componentRef.setInput('feedback', getFeedbackItem(text, getFeedbackReference(1, 2, false)));
        fixture.detectChanges();

        expect(comp.text).toBe('');
    });

    it('should set the text to the feedback text', () => {
        const text = 'dummy text';
        fixture.componentRef.setInput('feedback', getFeedbackItem(text, getFeedbackReference(1, 2, false)));
        fixture.detectChanges();

        expect(comp.text).toBe(text);
    });

    it('should not fetch long feedback if it does not exist', fakeAsync(() => {
        fixture.componentRef.setInput('feedback', getFeedbackItem('', getFeedbackReference(1, 2, false)));
        fixture.detectChanges();
        tick();

        expect(getLongFeedbackStub).not.toHaveBeenCalled();
    }));

    it('should fetch long feedback', fakeAsync(() => {
        const longFeedbackText: string = 'long feedback text';
        getLongFeedbackStub.mockReturnValue(of(new HttpResponse<string>({ body: longFeedbackText })));

        fixture.componentRef.setInput('feedback', getFeedbackItem('', getFeedbackReference(1, 2, true)));
        fixture.detectChanges();
        tick();

        expect(getLongFeedbackStub).toHaveBeenCalledOnce();
        expect(getLongFeedbackStub).toHaveBeenCalledWith(2);
        expect(comp.text).toBe(longFeedbackText);
        expect(comp.downloadText).toBeUndefined();
        expect(comp.downloadFilename).toBeUndefined();
    }));

    it('should create a download link for very long feedback', fakeAsync(() => {
        const longFeedbackText = '0'.repeat(100_000);
        getLongFeedbackStub.mockReturnValue(of(new HttpResponse<string>({ body: longFeedbackText })));

        fixture.componentRef.setInput('feedback', getFeedbackItem('short version', getFeedbackReference(1, 2, true)));
        fixture.detectChanges();
        tick();

        expect(comp.text).toBe('short version');
        expect(comp.downloadFilename).toBe('feedback_2.txt');
        expect(comp.downloadText).toContain('data:text/plain;charset=utf-8,');
        expect(comp.downloadText).toContain(longFeedbackText);
    }));

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
