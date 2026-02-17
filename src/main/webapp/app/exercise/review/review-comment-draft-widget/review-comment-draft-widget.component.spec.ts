import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ReviewCommentFacade } from 'app/exercise/review/review-comment-facade.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';

describe('ReviewCommentDraftWidgetComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ReviewCommentDraftWidgetComponent>;
    let comp: ReviewCommentDraftWidgetComponent;
    let facade: any;

    beforeEach(async () => {
        facade = {
            ensureDraft: vi.fn(),
            getDraftText: vi.fn(() => 'text'),
            isDraftSubmitting: vi.fn(() => false),
            submitCreateThread: vi.fn(),
            setDraftText: vi.fn(),
            removeDraft: vi.fn(),
        };
        await TestBed.configureTestingModule({
            imports: [ReviewCommentDraftWidgetComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ReviewCommentFacade, useValue: facade },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ReviewCommentDraftWidgetComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('location', {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            lineNumber: 3,
            filePath: 'file.java',
        });
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should submit a thread when allowed', () => {
        comp.submitDraft();

        expect(facade.submitCreateThread).toHaveBeenCalledWith({
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 3,
            initialFilePath: 'file.java',
            auxiliaryRepositoryId: undefined,
            initialComment: { contentType: 'USER', text: 'text' },
        });
    });

    it('should store draft text changes', () => {
        comp.onDraftTextChanged('updated');

        expect(facade.setDraftText).toHaveBeenCalledWith(
            {
                targetType: CommentThreadLocationType.TEMPLATE_REPO,
                lineNumber: 3,
                filePath: 'file.java',
            },
            'updated',
        );
    });

    it('should block submit when canSubmit is false', () => {
        fixture.componentRef.setInput('canSubmit', false);
        fixture.detectChanges();

        comp.submitDraft();

        expect(facade.submitCreateThread).not.toHaveBeenCalled();
        const submitButton = fixture.nativeElement.querySelector('[data-testid="review-draft-submit"]');
        const errorMessage = fixture.nativeElement.querySelector('.monaco-review-comment-error');
        expect(submitButton.disabled).toBe(true);
        expect(errorMessage).not.toBeNull();
    });

    it('should block submit while operation is pending', () => {
        facade.isDraftSubmitting.mockReturnValue(true);
        fixture.detectChanges();

        comp.submitDraft();

        expect(facade.submitCreateThread).not.toHaveBeenCalled();
    });

    it('should remove draft and emit cancel', () => {
        const cancelSpy = vi.fn();
        comp.onCancel.subscribe(cancelSpy);

        comp.cancel();

        expect(facade.removeDraft).toHaveBeenCalledWith({
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            lineNumber: 3,
            filePath: 'file.java',
        });
        expect(cancelSpy).toHaveBeenCalled();
    });

    it('should ensure draft on init', () => {
        expect(facade.ensureDraft).toHaveBeenCalledWith({
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            lineNumber: 3,
            filePath: 'file.java',
        });
    });
});
