import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FeedbackTextComponent } from 'app/exercises/shared/feedback/text/feedback-text.component';
import { ArtemisTestModule } from '../../../test.module';
import { LongFeedbackTextService } from 'app/exercises/shared/feedback/long-feedback-text.service';
import { MockProvider } from 'ng-mocks';
import { FeedbackItem, FeedbackReference } from 'app/exercises/shared/feedback/item/feedback-item';
import { LongFeedbackText } from 'app/entities/long-feedback-text.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

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
        const longFeedbackText = {
            text: 'long feedback text',
        };
        getLongFeedbackStub.mockReturnValue(of(new HttpResponse<LongFeedbackText>({ body: longFeedbackText })));

        comp.feedback = getFeedbackItem('', getFeedbackReference(1, 2, true));

        comp.ngOnInit();
        tick();

        expect(getLongFeedbackStub).toHaveBeenCalledOnceWith(1, 2);
        expect(comp.text).toBe(longFeedbackText.text);
    }));

    const getFeedbackReference = (resultId: number, feedbackId: number, hasLongFeedback: boolean): FeedbackReference => {
        return {
            resultId,
            feedbackId,
            hasLongFeedback,
        };
    };

    const getFeedbackItem = (text: string | undefined, feedbackReference: FeedbackReference): FeedbackItem => {
        return {
            credits: undefined,
            name: 'ignored',
            type: 'Test',
            text,
            feedbackReference,
        };
    };
});
