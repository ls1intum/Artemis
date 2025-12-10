import { InlineCommentService } from './inline-comment.service';
import { InlineComment } from '../model/inline-comment.model';

describe('InlineCommentService', () => {
    let service: InlineCommentService;

    beforeEach(() => {
        service = new InlineCommentService();
        localStorage.clear();
    });

    afterEach(() => localStorage.clear());

    describe('setExerciseContext', () => {
        it('should set context and handle context changes', () => {
            service.setExerciseContext(123);
            expect(service.hasPendingComments()).toBeFalse();

            service.addComment(1, 5, 'Test instruction');
            service.setExerciseContext(123); // Same ID - should keep comments
            expect(service.pendingCount()).toBe(1);

            service.setExerciseContext(456); // Different ID - should clear
            expect(service.hasPendingComments()).toBeFalse();
        });
    });

    describe('addComment', () => {
        it('should add comment and persist to localStorage', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');

            expect(comment).toBeDefined();
            expect(comment.startLine).toBe(1);
            expect(comment.endLine).toBe(5);
            expect(comment.instruction).toBe('Test instruction');
            expect(comment.status).toBe('draft');
            expect(service.pendingCount()).toBe(1);

            const stored = localStorage.getItem('ai-inline-comments-123');
            expect(stored).toBeTruthy();
            expect(JSON.parse(stored!)).toHaveLength(1);
        });
    });

    describe('addExistingComment', () => {
        it('should add an existing comment object', () => {
            service.setExerciseContext(123);
            const existingComment: InlineComment = {
                id: 'test-id',
                startLine: 1,
                endLine: 5,
                instruction: 'Test instruction',
                status: 'pending',
                createdAt: new Date(),
            };

            service.addExistingComment(existingComment);
            expect(service.pendingCount()).toBe(1);
            expect(service.getComment('test-id')).toEqual(existingComment);
        });
    });

    describe('removeComment', () => {
        it('should remove comment by ID and do nothing for non-existent ID', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');
            expect(service.pendingCount()).toBe(1);

            service.removeComment('non-existent-id');
            expect(service.pendingCount()).toBe(1);

            service.removeComment(comment.id);
            expect(service.pendingCount()).toBe(0);
        });
    });

    describe('updateStatus', () => {
        it('should update status and persist to localStorage', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');
            expect(comment.status).toBe('draft');

            service.updateStatus(comment.id, 'applying');
            expect(service.getComment(comment.id)?.status).toBe('applying');

            service.updateStatus(comment.id, 'error');
            const stored = JSON.parse(localStorage.getItem('ai-inline-comments-123')!);
            expect(stored[0].status).toBe('error');
        });
    });

    describe('getComment', () => {
        it('should return comment if found, undefined if not', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');

            expect(service.getComment(comment.id)).toEqual(comment);
            expect(service.getComment('non-existent-id')).toBeUndefined();
        });
    });

    describe('getCommentsReadyToApply', () => {
        it('should return only pending comments', () => {
            service.setExerciseContext(123);
            const comment1 = service.addComment(1, 5, 'Test 1');
            const comment2 = service.addComment(6, 10, 'Test 2');
            service.updateStatus(comment1.id, 'pending');
            service.updateStatus(comment2.id, 'applying');

            const readyToApply = service.getCommentsReadyToApply();
            expect(readyToApply).toHaveLength(1);
            expect(readyToApply[0].id).toBe(comment1.id);
        });
    });

    describe('clearAll and markApplied', () => {
        it('should clear all comments and localStorage', () => {
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test 1');
            service.addComment(6, 10, 'Test 2');
            expect(service.pendingCount()).toBe(2);

            service.clearAll();
            expect(service.pendingCount()).toBe(0);
            expect(localStorage.getItem('ai-inline-comments-123')).toBeNull();
        });

        it('should remove comments when marked as applied', () => {
            service.setExerciseContext(123);
            const comment1 = service.addComment(1, 5, 'Test 1');
            const comment2 = service.addComment(6, 10, 'Test 2');
            service.addComment(11, 15, 'Test 3');
            expect(service.pendingCount()).toBe(3);

            service.markApplied(comment1.id);
            expect(service.pendingCount()).toBe(2);

            service.markAllApplied([comment2.id]);
            expect(service.pendingCount()).toBe(1);
        });
    });

    describe('clearContext', () => {
        it('should clear the exercise context and comments', () => {
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test instruction');
            expect(service.pendingCount()).toBe(1);

            service.clearContext();
            expect(service.hasPendingComments()).toBeFalse();
        });
    });

    describe('persistence', () => {
        it('should load comments from localStorage and handle corrupted data', () => {
            // Save and reload
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test instruction');

            const newService = new InlineCommentService();
            newService.setExerciseContext(123);
            expect(newService.pendingCount()).toBe(1);

            // Handle corrupted data
            localStorage.setItem('ai-inline-comments-456', 'invalid json');
            newService.setExerciseContext(456);
            expect(newService.hasPendingComments()).toBeFalse();
            expect(localStorage.getItem('ai-inline-comments-456')).toBeNull();
        });
    });
});
