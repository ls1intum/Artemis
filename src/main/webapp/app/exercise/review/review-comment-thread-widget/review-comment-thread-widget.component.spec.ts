import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { signal } from '@angular/core';

describe('ReviewCommentThreadWidgetComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ReviewCommentThreadWidgetComponent>;
    let comp: ReviewCommentThreadWidgetComponent;
    let reviewCommentService: any;

    beforeEach(async () => {
        reviewCommentService = {
            deleteCommentInContext: vi.fn(),
            createReplyInContext: vi.fn(),
            updateCommentInContext: vi.fn(),
            toggleResolvedInContext: vi.fn(),
            threads: signal([]),
        };

        await TestBed.configureTestingModule({
            imports: [ReviewCommentThreadWidgetComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExerciseReviewCommentService, useValue: reviewCommentService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ReviewCommentThreadWidgetComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('thread', { id: 1, resolved: false, comments: [] } as any);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize collapsed state', () => {
        fixture.componentRef.setInput('initialCollapsed', true);
        fixture.componentRef.setInput('thread', { id: 2, resolved: false, comments: [] } as any);
        comp.ngOnInit();
        expect(comp.showThreadBody()).toBe(false);
    });

    it('should emit delete on deleteComment', () => {
        comp.deleteComment(5);
        expect(reviewCommentService.deleteCommentInContext).toHaveBeenCalledWith(5);
    });

    it('should update comment on saveEditing and clear editing state', () => {
        reviewCommentService.updateCommentInContext.mockImplementation((_id: number, _content: any, onSuccess?: () => void) => onSuccess?.());
        comp.editingCommentId.set(7);
        comp.editingCommentType.set(CommentType.USER);
        comp.editText.set('  updated  ');
        comp.saveEditing();

        expect(reviewCommentService.updateCommentInContext).toHaveBeenCalledWith(7, { contentType: CommentContentType.USER, text: 'updated' }, expect.any(Function));
        expect(comp.editingCommentId()).toBeUndefined();
        expect(comp.editText()).toBe('');
    });

    it('should not emit update when edit text is empty', () => {
        comp.editingCommentId.set(4);
        comp.editingCommentType.set(CommentType.USER);
        comp.editText.set('   ');
        comp.saveEditing();

        expect(reviewCommentService.updateCommentInContext).not.toHaveBeenCalled();
    });

    it('should not emit update when editing comment type is not USER', () => {
        comp.editingCommentId.set(4);
        comp.editingCommentType.set(CommentType.CONSISTENCY_CHECK);
        comp.editText.set('updated');
        comp.saveEditing();

        expect(reviewCommentService.updateCommentInContext).not.toHaveBeenCalled();
    });

    it('should create reply and clear reply text', () => {
        reviewCommentService.createReplyInContext.mockImplementation((_threadId: number, _comment: any, onSuccess?: () => void) => onSuccess?.());
        comp.replyText.set('  reply  ');
        comp.submitReply();

        expect(reviewCommentService.createReplyInContext).toHaveBeenCalledWith(1, { contentType: CommentContentType.USER, text: 'reply' }, expect.any(Function));
        expect(comp.replyText()).toBe('');
    });

    it('should not emit reply when reply text is empty', () => {
        comp.replyText.set('   ');
        comp.submitReply();

        expect(reviewCommentService.createReplyInContext).not.toHaveBeenCalled();
    });

    it('should keep edit text when update is not confirmed', () => {
        comp.editingCommentId.set(7);
        comp.editingCommentType.set(CommentType.USER);
        comp.editText.set('updated');

        comp.saveEditing();

        expect(reviewCommentService.updateCommentInContext).toHaveBeenCalledWith(7, { contentType: CommentContentType.USER, text: 'updated' }, expect.any(Function));
        expect(comp.editingCommentId()).toBe(7);
        expect(comp.editText()).toBe('updated');
    });

    it('should keep reply text when reply creation is not confirmed', () => {
        comp.replyText.set('reply');

        comp.submitReply();

        expect(reviewCommentService.createReplyInContext).toHaveBeenCalledWith(1, { contentType: CommentContentType.USER, text: 'reply' }, expect.any(Function));
        expect(comp.replyText()).toBe('reply');
    });

    it('should toggle resolved and collapse when resolving', () => {
        const collapseSpy = vi.fn();
        comp.onToggleCollapse.subscribe(collapseSpy);
        fixture.componentRef.setInput('thread', { id: 1, resolved: false } as any);

        comp.toggleResolved();

        expect(reviewCommentService.toggleResolvedInContext).toHaveBeenCalledWith(1, true);
        expect(comp.showThreadBody()).toBe(false);
        expect(collapseSpy).toHaveBeenCalledWith(true);
    });

    it('should toggle thread body and emit collapse state', () => {
        const collapseSpy = vi.fn();
        comp.onToggleCollapse.subscribe(collapseSpy);
        comp.showThreadBody.set(true);

        comp.toggleThreadBody();

        expect(comp.showThreadBody()).toBe(false);
        expect(collapseSpy).toHaveBeenCalledWith(true);
    });

    it('should detect edited comments', () => {
        const comment = {
            createdDate: '2024-01-01T00:00:00Z',
            lastModifiedDate: '2024-01-02T00:00:00Z',
        } as any;

        expect(comp.isEdited(comment)).toBe(true);
    });

    it('should order comments by date then id', () => {
        const thread = {
            comments: [
                { id: 2, createdDate: '2024-01-02T00:00:00Z' },
                { id: 1, createdDate: '2024-01-01T00:00:00Z' },
                { id: 3, createdDate: '2024-01-02T00:00:00Z' },
            ],
        } as any;
        fixture.componentRef.setInput('thread', thread);

        const ordered = comp.orderedComments();
        expect(ordered.map((c) => c.id)).toEqual([1, 2, 3]);
    });

    it('should format user and non-user comment content', () => {
        const userComment = {
            content: { contentType: CommentContentType.USER, text: 'hello' },
        } as any;
        const nonUserComment = {
            content: {
                contentType: CommentContentType.CONSISTENCY_CHECK,
                severity: ConsistencyIssue.SeverityEnum.High,
                category: ConsistencyIssue.CategoryEnum.MethodParameterMismatch,
                text: 'msg',
            },
        } as any;

        expect(comp.formatReviewCommentText(userComment)).toBe('hello');
        expect(comp.formatReviewCommentText(nonUserComment)).toBe('msg');
    });

    it('should detect consistency-issue thread based on first comment', () => {
        fixture.componentRef.setInput('thread', {
            id: 1,
            resolved: false,
            comments: [
                {
                    id: 3,
                    type: CommentType.CONSISTENCY_CHECK,
                    createdDate: '2024-01-01T00:00:00Z',
                    content: {
                        contentType: CommentContentType.CONSISTENCY_CHECK,
                        severity: ConsistencyIssue.SeverityEnum.High,
                        category: ConsistencyIssue.CategoryEnum.MethodParameterMismatch,
                        text: 'issue',
                    },
                },
                {
                    id: 4,
                    type: CommentType.USER,
                    createdDate: '2024-01-02T00:00:00Z',
                    content: { contentType: CommentContentType.USER, text: 'reply' },
                },
            ],
        } as any);

        expect(comp.isConsistencyIssueThread()).toBe(true);
        expect(comp.firstConsistencyIssueContent()?.text).toBe('issue');
    });

    it('should set edit text when starting editing', () => {
        const comment = {
            id: 1,
            type: CommentType.USER,
            content: { contentType: CommentContentType.USER, text: 'note' },
        } as any;

        comp.startEditing(comment);
        expect(comp.editingCommentId()).toBe(1);
        expect(comp.editText()).toBe('note');
    });

    it('should ignore startEditing for non-user comments', () => {
        const comment = {
            id: 2,
            type: CommentType.CONSISTENCY_CHECK,
            content: {
                contentType: CommentContentType.CONSISTENCY_CHECK,
                severity: ConsistencyIssue.SeverityEnum.Low,
                category: ConsistencyIssue.CategoryEnum.IdentifierNamingInconsistency,
                text: 'system note',
            },
        } as any;

        comp.startEditing(comment);
        expect(comp.editingCommentId()).toBeUndefined();
        expect(comp.editText()).toBe('');
    });
});
