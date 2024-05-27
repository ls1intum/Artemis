import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SelfLearningFeedbackRequest } from 'app/entities/self-learning-feedback-request.model';
import dayjs from 'dayjs/esm';
import { SelfLearningFeedbackRequestComponent } from 'app/exercises/shared/self-learning-feedback-request/self-learning-feedback-request.component';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('SelfLearningFeedbackRequestComponent', () => {
    let comp: SelfLearningFeedbackRequestComponent;
    let fixture: ComponentFixture<SelfLearningFeedbackRequestComponent>;
    let requestMock: SelfLearningFeedbackRequest;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [SelfLearningFeedbackRequestComponent, TranslatePipeMock],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SelfLearningFeedbackRequestComponent);
                comp = fixture.componentInstance;

                requestMock = {
                    requestDateTime: dayjs(),
                    responseDateTime: undefined,
                    successful: undefined,
                } as SelfLearningFeedbackRequest;
                comp.selfLearningFeedbackRequest = requestMock;
                comp.showIcon = true;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });

    it('should handle timeout correctly', () => {
        jest.useFakeTimers();
        comp.ngOnInit();
        expect(comp.timedOut).toBeFalsy();
        jest.advanceTimersByTime(7200000);
        expect(comp.timedOut).toBeTruthy();
        jest.useRealTimers();
    });

    it('should clean up timeout on destroy', () => {
        jest.useFakeTimers();
        comp.ngOnInit();
        expect(jest.getTimerCount()).toBe(1);
        comp.ngOnDestroy();
        expect(jest.getTimerCount()).toBe(0);
        jest.useRealTimers();
    });

    describe('Conditional rendering tests', () => {
        it('should display failure message if request failed', () => {
            comp.selfLearningFeedbackRequest.successful = false;
            fixture.detectChanges();
            const icon = fixture.nativeElement.querySelector('.result-score-icon');
            const message = fixture.nativeElement.querySelector('#self-learning-feedback-failed span');
            expect(message.getAttribute('jhiTranslate')).toContain('artemisApp.result.resultString.automaticAIFeedbackFailed');
            expect(icon).toBeTruthy();
        });

        it('should display loading message if waiting for response', () => {
            comp.selfLearningFeedbackRequest.responseDateTime = undefined;
            fixture.detectChanges();
            const icon = fixture.nativeElement.querySelector('.result-score-icon');
            const message = fixture.nativeElement.querySelector('#self-learning-feedback-loading span');
            expect(icon).toBeTruthy();
            expect(message.getAttribute('jhiTranslate')).toContain('automaticAIFeedbackInProgress');
        });

        it('should display timed out message if timed out', () => {
            comp.timedOut = true;
            fixture.detectChanges();
            const icon = fixture.nativeElement.querySelector('.result-score-icon');
            const message = fixture.nativeElement.querySelector('#self-learning-feedback-timed-out span');
            expect(icon).toBeTruthy();
            expect(message.getAttribute('jhiTranslate')).toContain('automaticAIFeedbackTimedOut');
        });
    });
});
