import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ReviewCommentThreadWidgetComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ReviewCommentThreadWidgetComponent>;
    let comp: ReviewCommentThreadWidgetComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReviewCommentThreadWidgetComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
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
        expect(comp.showThreadBody).toBe(false);
    });

    it('should emit delete on deleteComment', () => {
        const deleteSpy = vi.fn();
        comp.onDelete.subscribe(deleteSpy);

        comp.deleteComment(5);
        expect(deleteSpy).toHaveBeenCalledWith(5);
    });

    it('should emit submit edit intent and clear local editing state', () => {
        const submitEditSpy = vi.fn();
        comp.onSubmitEdit.subscribe(submitEditSpy);

        comp.editingCommentId = 7;
        comp.editingCommentType = CommentType.USER;
        comp.submitEdit();

        expect(submitEditSpy).toHaveBeenCalledWith(7);
        expect(comp.editingCommentId).toBeUndefined();
    });

    it('should not emit submit edit intent when comment type is not USER', () => {
        const submitEditSpy = vi.fn();
        comp.onSubmitEdit.subscribe(submitEditSpy);

        comp.editingCommentId = 4;
        comp.editingCommentType = CommentType.CONSISTENCY_CHECK;
        comp.submitEdit();

        expect(submitEditSpy).not.toHaveBeenCalled();
    });

    it('should not emit submit edit intent while edit submission is pending', () => {
        const submitEditSpy = vi.fn();
        comp.onSubmitEdit.subscribe(submitEditSpy);
        fixture.componentRef.setInput('isEditSubmitting', true);
        fixture.detectChanges();

        comp.editingCommentId = 4;
        comp.editingCommentType = CommentType.USER;
        comp.submitEdit();

        expect(submitEditSpy).not.toHaveBeenCalled();
    });

    it('should emit edit draft changes with comment id', () => {
        const editChangeSpy = vi.fn();
        comp.onEditTextChange.subscribe(editChangeSpy);
        comp.editingCommentId = 15;

        comp.onEditDraftChanged('updated text');

        expect(editChangeSpy).toHaveBeenCalledWith({ commentId: 15, text: 'updated text' });
    });

    it('should emit reply submit intent', () => {
        const submitReplySpy = vi.fn();
        comp.onSubmitReply.subscribe(submitReplySpy);

        comp.submitReply();

        expect(submitReplySpy).toHaveBeenCalledOnce();
    });

    it('should not emit reply submit intent while pending', () => {
        const submitReplySpy = vi.fn();
        comp.onSubmitReply.subscribe(submitReplySpy);
        fixture.componentRef.setInput('isReplySubmitting', true);
        fixture.detectChanges();

        comp.submitReply();

        expect(submitReplySpy).not.toHaveBeenCalled();
    });

    it('should emit reply draft changes', () => {
        const replyChangeSpy = vi.fn();
        comp.onReplyTextChange.subscribe(replyChangeSpy);

        comp.onReplyDraftChanged('reply text');

        expect(replyChangeSpy).toHaveBeenCalledWith('reply text');
    });

    it('should toggle resolved and collapse when resolving', () => {
        const resolvedSpy = vi.fn();
        const collapseSpy = vi.fn();
        comp.onToggleResolved.subscribe(resolvedSpy);
        comp.onToggleCollapse.subscribe(collapseSpy);
        fixture.componentRef.setInput('thread', { id: 1, resolved: false } as any);

        comp.toggleResolved();

        expect(resolvedSpy).toHaveBeenCalledWith(true);
        expect(comp.showThreadBody).toBe(false);
        expect(collapseSpy).toHaveBeenCalledWith(true);
    });

    it('should not toggle resolved while resolve operation is pending', () => {
        const resolvedSpy = vi.fn();
        comp.onToggleResolved.subscribe(resolvedSpy);
        fixture.componentRef.setInput('isResolveSubmitting', true);
        fixture.componentRef.setInput('thread', { id: 1, resolved: false } as any);
        fixture.detectChanges();

        comp.toggleResolved();

        expect(resolvedSpy).not.toHaveBeenCalled();
    });

    it('should toggle thread body and emit collapse state', () => {
        const collapseSpy = vi.fn();
        comp.onToggleCollapse.subscribe(collapseSpy);
        comp.showThreadBody = true;

        comp.toggleThreadBody();

        expect(comp.showThreadBody).toBe(false);
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
            content: { contentType: 'USER', text: 'hello' },
        } as any;
        const nonUserComment = {
            content: { contentType: 'CONSISTENCY_CHECK', severity: 'ERROR', category: 'CODE', text: 'msg' },
        } as any;

        expect(comp.formatReviewCommentText(userComment)).toBe('hello');
        expect(comp.formatReviewCommentText(nonUserComment)).toBe('ERROR - CODE - msg');
    });

    it('should emit start-edit intent when starting editing', () => {
        const startEditSpy = vi.fn();
        comp.onStartEdit.subscribe(startEditSpy);
        const comment = {
            id: 1,
            type: 'USER',
            content: { contentType: 'USER', text: 'note' },
        } as any;

        comp.startEditing(comment);
        expect(comp.editingCommentId).toBe(1);
        expect(startEditSpy).toHaveBeenCalledWith({ commentId: 1, initialText: 'note' });
    });

    it('should emit cancel-edit intent when cancelling edit mode', () => {
        const cancelEditSpy = vi.fn();
        comp.onCancelEdit.subscribe(cancelEditSpy);
        comp.editingCommentId = 3;
        comp.editingCommentType = CommentType.USER;

        comp.cancelEditing();

        expect(cancelEditSpy).toHaveBeenCalledWith(3);
        expect(comp.editingCommentId).toBeUndefined();
    });

    it('should ignore startEditing for non-user comments', () => {
        const startEditSpy = vi.fn();
        comp.onStartEdit.subscribe(startEditSpy);
        const comment = {
            id: 2,
            type: 'CONSISTENCY_CHECK',
            content: { contentType: 'CONSISTENCY_CHECK', text: 'system note' },
        } as any;

        comp.startEditing(comment);
        expect(comp.editingCommentId).toBeUndefined();
        expect(startEditSpy).not.toHaveBeenCalled();
    });
});
