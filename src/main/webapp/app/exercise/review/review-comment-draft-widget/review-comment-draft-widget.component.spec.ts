import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ReviewCommentDraftWidgetComponent', () => {
    let fixture: ComponentFixture<ReviewCommentDraftWidgetComponent>;
    let comp: ReviewCommentDraftWidgetComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReviewCommentDraftWidgetComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ReviewCommentDraftWidgetComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should emit submit when allowed and text is non-empty', () => {
        const submitSpy = jest.fn();
        comp.onSubmit.subscribe(submitSpy);

        comp.text = '  hello  ';
        comp.submit();

        expect(submitSpy).toHaveBeenCalledWith('hello');
    });

    it('should not emit submit when text is empty', () => {
        const submitSpy = jest.fn();
        comp.onSubmit.subscribe(submitSpy);

        comp.text = '   ';
        comp.submit();

        expect(submitSpy).not.toHaveBeenCalled();
    });

    it('should block submit when canSubmit is false', () => {
        const submitSpy = jest.fn();
        comp.onSubmit.subscribe(submitSpy);
        fixture.componentRef.setInput('canSubmit', false);
        fixture.detectChanges();

        comp.text = 'text';
        comp.submit();

        expect(submitSpy).not.toHaveBeenCalled();
        const submitButton = fixture.nativeElement.querySelector('[data-testid="review-draft-submit"]');
        const errorMessage = fixture.nativeElement.querySelector('.monaco-review-comment-error');
        expect(submitButton.disabled).toBeTrue();
        expect(errorMessage).not.toBeNull();
    });

    it('should emit cancel and reset error flag', () => {
        const cancelSpy = jest.fn();
        comp.onCancel.subscribe(cancelSpy);

        comp.cancel();

        expect(cancelSpy).toHaveBeenCalled();
    });
});
