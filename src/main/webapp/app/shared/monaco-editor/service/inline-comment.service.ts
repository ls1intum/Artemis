import { Injectable, computed, signal } from '@angular/core';
import { InlineComment, InlineCommentStatus, SerializedInlineComment, createInlineComment, deserializeComment, serializeComment } from '../model/inline-comment.model';

/**
 * Service for managing inline comments during AI-assisted problem statement refinement.
 *
 * This service uses localStorage for persistence, keyed by exercise ID.
 * Comments are ephemeral and designed to be applied then discarded.
 */
@Injectable({ providedIn: 'root' })
export class InlineCommentService {
    private static readonly STORAGE_KEY_PREFIX = 'ai-inline-comments-';

    /** Current exercise context */
    private currentExerciseId = signal<number | undefined>(undefined);

    /** All pending comments for the current exercise */
    private pendingComments = signal<InlineComment[]>([]);

    /** Whether there are any pending comments */
    readonly hasPendingComments = computed(() => this.pendingComments().length > 0);

    /** Number of pending comments */
    readonly pendingCount = computed(() => this.pendingComments().length);

    /** Storage key for current exercise */
    private storageKey = computed(() => {
        const exerciseId = this.currentExerciseId();
        return exerciseId ? `${InlineCommentService.STORAGE_KEY_PREFIX}${exerciseId}` : undefined;
    });

    /**
     * Sets the exercise context and loads any persisted comments.
     * Call this when the exercise is loaded or changes.
     */
    setExerciseContext(exerciseId: number): void {
        if (this.currentExerciseId() !== exerciseId) {
            this.currentExerciseId.set(exerciseId);
            this.loadFromStorage();
        }
    }

    /**
     * Returns the pending comments signal for reactive access.
     */
    getPendingComments() {
        return this.pendingComments.asReadonly();
    }

    /**
     * Adds a new comment and persists to localStorage.
     */
    addComment(startLine: number, endLine: number, instruction: string): InlineComment {
        const comment = createInlineComment(startLine, endLine, instruction);
        this.pendingComments.update((comments) => [...comments, comment]);
        this.persistToStorage();
        return comment;
    }

    /**
     * Adds an existing comment object (e.g., when restoring from draft).
     */
    addExistingComment(comment: InlineComment): void {
        this.pendingComments.update((comments) => [...comments, comment]);
        this.persistToStorage();
    }

    /**
     * Removes a comment by ID.
     */
    removeComment(id: string): void {
        this.pendingComments.update((comments) => comments.filter((c) => c.id !== id));
        this.persistToStorage();
    }

    /**
     * Updates the status of a comment.
     */
    updateStatus(id: string, status: InlineCommentStatus): void {
        this.pendingComments.update((comments) => comments.map((c) => (c.id === id ? { ...c, status } : c)));
        this.persistToStorage();
    }

    /**
     * Gets a specific comment by ID.
     */
    getComment(id: string): InlineComment | undefined {
        return this.pendingComments().find((c) => c.id === id);
    }

    /**
     * Returns comments that are ready to be applied (status is 'pending').
     */
    getCommentsReadyToApply(): InlineComment[] {
        return this.pendingComments().filter((c) => c.status === 'pending');
    }

    /**
     * Clears all comments for the current exercise.
     */
    clearAll(): void {
        this.pendingComments.set([]);
        this.clearStorage();
    }

    /**
     * Marks a comment as applied and removes it from the list.
     */
    markApplied(id: string): void {
        this.removeComment(id);
    }

    /**
     * Marks multiple comments as applied and removes them.
     */
    markAllApplied(ids: string[]): void {
        this.pendingComments.update((comments) => comments.filter((c) => !ids.includes(c.id)));
        this.persistToStorage();
    }

    private persistToStorage(): void {
        const key = this.storageKey();
        if (!key) {
            return;
        }

        const comments = this.pendingComments();
        if (comments.length === 0) {
            localStorage.removeItem(key);
        } else {
            const serialized = comments.map(serializeComment);
            localStorage.setItem(key, JSON.stringify(serialized));
        }
    }

    private loadFromStorage(): void {
        const key = this.storageKey();
        if (!key) {
            this.pendingComments.set([]);
            return;
        }

        const stored = localStorage.getItem(key);
        if (!stored) {
            this.pendingComments.set([]);
            return;
        }

        try {
            const serialized: SerializedInlineComment[] = JSON.parse(stored);
            const comments = serialized.map(deserializeComment);
            this.pendingComments.set(comments);
        } catch {
            // Corrupted storage, clear it
            localStorage.removeItem(key);
            this.pendingComments.set([]);
        }
    }

    private clearStorage(): void {
        const key = this.storageKey();
        if (key) {
            localStorage.removeItem(key);
        }
    }

    /**
     * Clears the exercise context (call on component destroy).
     */
    clearContext(): void {
        this.currentExerciseId.set(undefined);
        this.pendingComments.set([]);
    }
}
