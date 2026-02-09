import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ReviewCommentThreadWidgetComponent', () => {
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
        jest.restoreAllMocks();
    });

    it('should initialize collapsed state', () => {
        fixture.componentRef.setInput('initialCollapsed', true);
        fixture.componentRef.setInput('thread', { id: 2, resolved: false, comments: [] } as any);
        comp.ngOnInit();
        expect(comp.showThreadBody).toBeFalse();
    });

    it('should emit delete on deleteComment', () => {
        const deleteSpy = jest.fn();
        comp.onDelete.subscribe(deleteSpy);

        comp.deleteComment(5);
        expect(deleteSpy).toHaveBeenCalledWith(5);
    });

    it('should emit update on saveEditing and clear editing state', () => {
        const updateSpy = jest.fn();
        comp.onUpdate.subscribe(updateSpy);

        comp.editingCommentId = 7;
        comp.editText = '  updated  ';
        comp.saveEditing();

        expect(updateSpy).toHaveBeenCalledWith({ commentId: 7, content: { contentType: 'USER', text: 'updated' } });
        expect(comp.editingCommentId).toBeUndefined();
        expect(comp.editText).toBe('');
    });

    it('should not emit update when edit text is empty', () => {
        const updateSpy = jest.fn();
        comp.onUpdate.subscribe(updateSpy);

        comp.editingCommentId = 4;
        comp.editText = '   ';
        comp.saveEditing();

        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('should emit reply and clear reply text', () => {
        const replySpy = jest.fn();
        comp.onReply.subscribe(replySpy);

        comp.replyText = '  reply  ';
        comp.submitReply();

        expect(replySpy).toHaveBeenCalledWith({ contentType: 'USER', text: 'reply' });
        expect(comp.replyText).toBe('');
    });

    it('should not emit reply when reply text is empty', () => {
        const replySpy = jest.fn();
        comp.onReply.subscribe(replySpy);

        comp.replyText = '   ';
        comp.submitReply();

        expect(replySpy).not.toHaveBeenCalled();
    });

    it('should toggle resolved and collapse when resolving', () => {
        const resolvedSpy = jest.fn();
        const collapseSpy = jest.fn();
        comp.onToggleResolved.subscribe(resolvedSpy);
        comp.onToggleCollapse.subscribe(collapseSpy);
        fixture.componentRef.setInput('thread', { id: 1, resolved: false } as any);

        comp.toggleResolved();

        expect(resolvedSpy).toHaveBeenCalledWith(true);
        expect(comp.showThreadBody).toBeFalse();
        expect(collapseSpy).toHaveBeenCalledWith(true);
    });

    it('should toggle thread body and emit collapse state', () => {
        const collapseSpy = jest.fn();
        comp.onToggleCollapse.subscribe(collapseSpy);
        comp.showThreadBody = true;

        comp.toggleThreadBody();

        expect(comp.showThreadBody).toBeFalse();
        expect(collapseSpy).toHaveBeenCalledWith(true);
    });

    it('should detect edited comments', () => {
        const comment = {
            createdDate: '2024-01-01T00:00:00Z',
            lastModifiedDate: '2024-01-02T00:00:00Z',
        } as any;

        expect(comp.isEdited(comment)).toBeTrue();
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

    it('should return author name or fallback', () => {
        const withName = { authorName: 'Alice' } as any;
        const empty = {} as any;

        expect(comp.getCommentAuthorName(withName)).toBe('Alice');
        expect(comp.getCommentAuthorName(empty)).toBe('[Artemis User]');
    });

    it('should set edit text when starting editing', () => {
        const comment = {
            id: 1,
            type: 'USER',
            content: { contentType: 'USER', text: 'note' },
        } as any;

        comp.startEditing(comment);
        expect(comp.editingCommentId).toBe(1);
        expect(comp.editText).toBe('note');
    });
});
