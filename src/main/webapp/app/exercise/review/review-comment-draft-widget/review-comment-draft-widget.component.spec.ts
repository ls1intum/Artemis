import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ReviewCommentDraftWidgetComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ReviewCommentDraftWidgetComponent>;
    let comp: ReviewCommentDraftWidgetComponent;
    let reviewCommentService: any;

    beforeEach(async () => {
        reviewCommentService = {
            createThreadInContext: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [ReviewCommentDraftWidgetComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExerciseReviewCommentService, useValue: reviewCommentService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ReviewCommentDraftWidgetComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create a thread and emit submitted when allowed and text is non-empty', () => {
        reviewCommentService.createThreadInContext.mockImplementation((_payload: any, onSuccess?: () => void) => onSuccess?.());
        const submitSpy = vi.fn();
        comp.onSubmitted.subscribe(submitSpy);
        fixture.componentRef.setInput('targetType', CommentThreadLocationType.TEMPLATE_REPO);
        fixture.componentRef.setInput('lineNumber', 5);
        fixture.componentRef.setInput('filePath', 'src/main.ts');

        comp.text = '  hello  ';
        comp.submit();

        expect(reviewCommentService.createThreadInContext).toHaveBeenCalledWith(
            {
                targetType: CommentThreadLocationType.TEMPLATE_REPO,
                initialLineNumber: 5,
                initialFilePath: 'src/main.ts',
                initialComment: { contentType: CommentContentType.USER, text: 'hello' },
            },
            expect.any(Function),
        );
        expect(submitSpy).toHaveBeenCalled();
    });

    it('should not create a thread when text is empty', () => {
        const submitSpy = vi.fn();
        comp.onSubmitted.subscribe(submitSpy);
        fixture.componentRef.setInput('targetType', CommentThreadLocationType.TEMPLATE_REPO);
        fixture.componentRef.setInput('lineNumber', 5);
        fixture.componentRef.setInput('filePath', 'src/main.ts');

        comp.text = '   ';
        comp.submit();

        expect(reviewCommentService.createThreadInContext).not.toHaveBeenCalled();
        expect(submitSpy).not.toHaveBeenCalled();
    });

    it('should block submit when canSubmit is false', () => {
        const submitSpy = vi.fn();
        comp.onSubmitted.subscribe(submitSpy);
        fixture.componentRef.setInput('canSubmit', false);
        fixture.componentRef.setInput('targetType', CommentThreadLocationType.TEMPLATE_REPO);
        fixture.componentRef.setInput('lineNumber', 5);
        fixture.componentRef.setInput('filePath', 'src/main.ts');
        fixture.detectChanges();

        comp.text = 'text';
        comp.submit();

        expect(reviewCommentService.createThreadInContext).not.toHaveBeenCalled();
        expect(submitSpy).not.toHaveBeenCalled();
        const submitButton = fixture.nativeElement.querySelector('[data-testid="review-draft-submit"]');
        const errorMessage = fixture.nativeElement.querySelector('.monaco-review-comment-error');
        expect(submitButton.disabled).toBe(true);
        expect(errorMessage).not.toBeNull();
    });

    it('should not create a thread when required draft context is missing', () => {
        comp.text = 'text';
        comp.submit();

        expect(reviewCommentService.createThreadInContext).not.toHaveBeenCalled();
    });

    it('should omit initial file path for problem statement threads', () => {
        fixture.componentRef.setInput('targetType', CommentThreadLocationType.PROBLEM_STATEMENT);
        fixture.componentRef.setInput('lineNumber', 9);
        fixture.componentRef.setInput('filePath', 'problem_statement.md');

        comp.text = 'comment';
        comp.submit();

        expect(reviewCommentService.createThreadInContext).toHaveBeenCalledWith(
            {
                targetType: CommentThreadLocationType.PROBLEM_STATEMENT,
                initialLineNumber: 9,
                initialComment: { contentType: CommentContentType.USER, text: 'comment' },
            },
            expect.any(Function),
        );
    });

    it('should include auxiliary repository id for auxiliary drafts', () => {
        fixture.componentRef.setInput('targetType', CommentThreadLocationType.AUXILIARY_REPO);
        fixture.componentRef.setInput('lineNumber', 4);
        fixture.componentRef.setInput('filePath', 'src/aux.ts');
        fixture.componentRef.setInput('auxiliaryRepositoryId', 123);

        comp.text = 'aux';
        comp.submit();

        expect(reviewCommentService.createThreadInContext).toHaveBeenCalledWith(
            {
                targetType: CommentThreadLocationType.AUXILIARY_REPO,
                auxiliaryRepositoryId: 123,
                initialLineNumber: 4,
                initialFilePath: 'src/aux.ts',
                initialComment: { contentType: CommentContentType.USER, text: 'aux' },
            },
            expect.any(Function),
        );
    });

    it('should keep draft open when backend confirmation is missing', () => {
        const submitSpy = vi.fn();
        comp.onSubmitted.subscribe(submitSpy);
        fixture.componentRef.setInput('targetType', CommentThreadLocationType.TEMPLATE_REPO);
        fixture.componentRef.setInput('lineNumber', 5);
        fixture.componentRef.setInput('filePath', 'src/main.ts');

        comp.text = 'hello';
        comp.submit();

        expect(reviewCommentService.createThreadInContext).toHaveBeenCalled();
        expect(submitSpy).not.toHaveBeenCalled();
    });

    it('should emit cancel and reset error flag', () => {
        const cancelSpy = vi.fn();
        comp.onCancel.subscribe(cancelSpy);

        comp.cancel();

        expect(cancelSpy).toHaveBeenCalled();
    });
});
