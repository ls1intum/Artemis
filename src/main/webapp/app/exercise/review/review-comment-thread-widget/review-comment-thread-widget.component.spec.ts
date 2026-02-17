import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ReviewCommentFacade } from 'app/exercise/review/review-comment-facade.service';

describe('ReviewCommentThreadWidgetComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ReviewCommentThreadWidgetComponent>;
    let comp: ReviewCommentThreadWidgetComponent;
    let facade: any;

    beforeEach(async () => {
        facade = {
            isDeleteSubmitting: vi.fn(() => false),
            deleteComment: vi.fn(),
            startEditDraft: vi.fn(),
            cancelEditDraft: vi.fn(),
            getEditDraft: vi.fn(() => 'edited text'),
            setEditDraft: vi.fn(),
            isEditSubmitting: vi.fn(() => false),
            updateComment: vi.fn(),
            isReplySubmitting: vi.fn(() => false),
            getReplyDraft: vi.fn(() => 'reply text'),
            setReplyDraft: vi.fn(),
            createReply: vi.fn(),
            isResolveSubmitting: vi.fn(() => false),
            toggleResolved: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [ReviewCommentThreadWidgetComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ReviewCommentFacade, useValue: facade },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ReviewCommentThreadWidgetComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('thread', { id: 1, resolved: false, comments: [] } as any);
        fixture.detectChanges();
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

    it('should delete comment when no delete operation is pending', () => {
        comp.deleteComment(5);
        expect(facade.deleteComment).toHaveBeenCalledWith(5);
    });

    it('should not delete comment when delete operation is pending', () => {
        facade.isDeleteSubmitting.mockReturnValue(true);
        comp.deleteComment(5);
        expect(facade.deleteComment).not.toHaveBeenCalled();
    });

    it('should submit edit and clear local editing state', () => {
        comp.editingCommentId.set(7);
        comp.editingCommentType.set(CommentType.USER);

        comp.submitEdit();

        expect(facade.updateComment).toHaveBeenCalledWith(7, { contentType: 'USER', text: 'edited text' });
        expect(comp.editingCommentId()).toBeUndefined();
    });

    it('should not submit edit when comment type is not USER', () => {
        comp.editingCommentId.set(4);
        comp.editingCommentType.set(CommentType.CONSISTENCY_CHECK);

        comp.submitEdit();

        expect(facade.updateComment).not.toHaveBeenCalled();
    });

    it('should not submit edit while edit submission is pending', () => {
        facade.isEditSubmitting.mockReturnValue(true);
        comp.editingCommentId.set(4);
        comp.editingCommentType.set(CommentType.USER);

        comp.submitEdit();

        expect(facade.updateComment).not.toHaveBeenCalled();
    });

    it('should store edit draft changes with comment id', () => {
        comp.editingCommentId.set(15);

        comp.onEditDraftChanged('updated text');

        expect(facade.setEditDraft).toHaveBeenCalledWith(15, 'updated text');
    });

    it('should submit reply', () => {
        comp.submitReply();

        expect(facade.createReply).toHaveBeenCalledWith(1, { contentType: 'USER', text: 'reply text' });
    });

    it('should not submit reply while pending', () => {
        facade.isReplySubmitting.mockReturnValue(true);

        comp.submitReply();

        expect(facade.createReply).not.toHaveBeenCalled();
    });

    it('should store reply draft changes', () => {
        comp.onReplyDraftChanged('reply text');

        expect(facade.setReplyDraft).toHaveBeenCalledWith(1, 'reply text');
    });

    it('should toggle resolved and collapse when resolving', () => {
        const collapseSpy = vi.fn();
        comp.onToggleCollapse.subscribe(collapseSpy);
        fixture.componentRef.setInput('thread', { id: 1, resolved: false } as any);

        comp.toggleResolved();

        expect(facade.toggleResolved).toHaveBeenCalledWith(1, true);
        expect(comp.showThreadBody).toBe(false);
        expect(collapseSpy).toHaveBeenCalledWith(true);
    });

    it('should not toggle resolved while resolve operation is pending', () => {
        facade.isResolveSubmitting.mockReturnValue(true);
        fixture.componentRef.setInput('thread', { id: 1, resolved: false } as any);
        fixture.detectChanges();

        comp.toggleResolved();

        expect(facade.toggleResolved).not.toHaveBeenCalled();
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

    it('should initialize edit draft when starting editing', () => {
        const comment = {
            id: 1,
            type: 'USER',
            content: { contentType: 'USER', text: 'note' },
        } as any;

        comp.startEditing(comment);
        expect(comp.editingCommentId()).toBe(1);
        expect(facade.startEditDraft).toHaveBeenCalledWith(1, 'note');
    });

    it('should cancel edit mode and clear edit draft', () => {
        comp.editingCommentId.set(3);
        comp.editingCommentType.set(CommentType.USER);

        comp.cancelEditing();

        expect(facade.cancelEditDraft).toHaveBeenCalledWith(3);
        expect(comp.editingCommentId()).toBeUndefined();
    });

    it('should ignore startEditing for non-user comments', () => {
        const comment = {
            id: 2,
            type: 'CONSISTENCY_CHECK',
            content: { contentType: 'CONSISTENCY_CHECK', text: 'system note' },
        } as any;

        comp.startEditing(comment);
        expect(comp.editingCommentId()).toBeUndefined();
        expect(facade.startEditDraft).not.toHaveBeenCalled();
    });
});
