import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FeedbackTextComponent } from 'app/exercises/shared/feedback/text/feedback-text.component';
import { ArtemisTestModule } from '../../../test.module';
import { LongFeedbackTextService } from 'app/exercises/shared/feedback/long-feedback-text.service';
import { MockProvider } from 'ng-mocks';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { Feedback } from 'app/entities/feedback.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { Result } from 'app/entities/result.model';

describe('FeedbackTextComponent', () => {
    let fixture: ComponentFixture<FeedbackTextComponent>;
    let comp: FeedbackTextComponent;

    let getLongFeedbackStub: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [FeedbackTextComponent],
            providers: [MockProvider(LongFeedbackTextService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FeedbackTextComponent);
                comp = fixture.componentInstance;

                const longFeedbackTextService = fixture.debugElement.injector.get(LongFeedbackTextService);
                getLongFeedbackStub = jest.spyOn(longFeedbackTextService, 'find');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set an empty text if there is no feedback text', () => {
        const text = undefined;
        comp.feedback = getFeedbackItem(text, getFeedbackReference(1, 2, false));

        comp.ngOnInit();

        expect(comp.text).toBe('');
    });

    it('should set the text to the feedback text', () => {
        const text = 'dummy text';
        comp.feedback = getFeedbackItem(text, getFeedbackReference(1, 2, false));

        comp.ngOnInit();

        expect(comp.text).toBe(text);
    });

    it('should not fetch long feedback if it does not exist', fakeAsync(() => {
        comp.feedback = getFeedbackItem('', getFeedbackReference(1, 2, false));

        comp.ngOnInit();
        tick();

        expect(getLongFeedbackStub).not.toHaveBeenCalled();
    }));

    it('should fetch long feedback', fakeAsync(() => {
        const longFeedbackText: string = 'long feedback text';
        getLongFeedbackStub.mockReturnValue(of(new HttpResponse<string>({ body: longFeedbackText })));

        comp.feedback = getFeedbackItem('', getFeedbackReference(1, 2, true));

        comp.ngOnInit();
        tick();

        expect(getLongFeedbackStub).toHaveBeenCalledExactlyOnceWith(1, 2);
        expect(comp.text).toBe(longFeedbackText);
        expect(comp.downloadText).toBeUndefined();
        expect(comp.downloadFilename).toBeUndefined();
    }));

    it('should create a download link for very long feedback', fakeAsync(() => {
        const longFeedbackText = '0'.repeat(100_000);
        getLongFeedbackStub.mockReturnValue(of(new HttpResponse<string>({ body: longFeedbackText })));

        comp.feedback = getFeedbackItem('short version', getFeedbackReference(1, 2, true));

        comp.ngOnInit();
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
