import { InlineCommentService } from './inline-comment.service';
import { InlineComment } from '../model/inline-comment.model';

describe('InlineCommentService', () => {
    let service: InlineCommentService;

    beforeEach(() => {
        service = new InlineCommentService();
        // Clear localStorage before each test
        localStorage.clear();
    });

    afterEach(() => {
        localStorage.clear();
    });

    describe('setExerciseContext', () => {
        it('should set the exercise context and load from storage', () => {
            service.setExerciseContext(123);
            expect(service.hasPendingComments()).toBeFalse();
        });

        it('should not reload if same exercise ID is set', () => {
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test instruction');
            service.setExerciseContext(123); // Same ID
            expect(service.pendingCount()).toBe(1);
        });

        it('should load different comments when exercise context changes', () => {
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test instruction');

            service.setExerciseContext(456);
            expect(service.hasPendingComments()).toBeFalse();
        });
    });

    describe('addComment', () => {
        it('should add a new comment', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');

            expect(comment).toBeDefined();
            expect(comment.startLine).toBe(1);
            expect(comment.endLine).toBe(5);
            expect(comment.instruction).toBe('Test instruction');
            expect(comment.status).toBe('draft');
            expect(service.pendingCount()).toBe(1);
        });

        it('should persist to localStorage', () => {
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test instruction');

            const stored = localStorage.getItem('ai-inline-comments-123');
            expect(stored).toBeTruthy();
            const parsed = JSON.parse(stored!);
            expect(parsed).toHaveLength(1);
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
        it('should remove a comment by ID', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');
            expect(service.pendingCount()).toBe(1);

            service.removeComment(comment.id);
            expect(service.pendingCount()).toBe(0);
        });

        it('should do nothing if comment ID does not exist', () => {
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test instruction');
            service.removeComment('non-existent-id');
            expect(service.pendingCount()).toBe(1);
        });
    });

    describe('updateStatus', () => {
        it('should update the status of a comment', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');
            expect(comment.status).toBe('draft');

            service.updateStatus(comment.id, 'applying');
            const updated = service.getComment(comment.id);
            expect(updated?.status).toBe('applying');
        });

        it('should persist the status change to localStorage', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');
            service.updateStatus(comment.id, 'error');

            const stored = localStorage.getItem('ai-inline-comments-123');
            const parsed = JSON.parse(stored!);
            expect(parsed[0].status).toBe('error');
        });
    });

    describe('getComment', () => {
        it('should return the comment if found', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');
            const retrieved = service.getComment(comment.id);
            expect(retrieved).toEqual(comment);
        });

        it('should return undefined if not found', () => {
            service.setExerciseContext(123);
            const retrieved = service.getComment('non-existent-id');
            expect(retrieved).toBeUndefined();
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

    describe('clearAll', () => {
        it('should clear all comments', () => {
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test 1');
            service.addComment(6, 10, 'Test 2');
            expect(service.pendingCount()).toBe(2);

            service.clearAll();
            expect(service.pendingCount()).toBe(0);
        });

        it('should clear localStorage', () => {
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test 1');
            service.clearAll();

            const stored = localStorage.getItem('ai-inline-comments-123');
            expect(stored).toBeNull();
        });
    });

    describe('markApplied', () => {
        it('should remove the comment when marked as applied', () => {
            service.setExerciseContext(123);
            const comment = service.addComment(1, 5, 'Test instruction');
            expect(service.pendingCount()).toBe(1);

            service.markApplied(comment.id);
            expect(service.pendingCount()).toBe(0);
        });
    });

    describe('markAllApplied', () => {
        it('should remove multiple comments when marked as applied', () => {
            service.setExerciseContext(123);
            const comment1 = service.addComment(1, 5, 'Test 1');
            const comment2 = service.addComment(6, 10, 'Test 2');
            service.addComment(11, 15, 'Test 3');
            expect(service.pendingCount()).toBe(3);

            service.markAllApplied([comment1.id, comment2.id]);
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
        it('should load comments from localStorage on setExerciseContext', () => {
            // First, save some comments
            service.setExerciseContext(123);
            service.addComment(1, 5, 'Test instruction');

            // Create a new service instance and load
            const newService = new InlineCommentService();
            newService.setExerciseContext(123);

            expect(newService.pendingCount()).toBe(1);
        });

        it('should handle corrupted localStorage gracefully', () => {
            localStorage.setItem('ai-inline-comments-123', 'invalid json');

            service.setExerciseContext(123);
            expect(service.hasPendingComments()).toBeFalse();

            // Should have cleared the corrupted data
            expect(localStorage.getItem('ai-inline-comments-123')).toBeNull();
        });
    });
});
