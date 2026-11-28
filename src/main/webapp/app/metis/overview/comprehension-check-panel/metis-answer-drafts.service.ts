import { Service } from '@angular/core';

/** The draft of the answer to the question that the check of an exercise shows. */
interface KeptDraft {
    questionId: number;
    text: string;
}

/**
 * Keeps the draft of the answer to the open question of each exercise for the rest of the session, outside the comprehension
 * check panel that shows it. The panel is created anew whenever the view around it is: the split view of the exercise builds
 * its panels again when it switches between the side-by-side and the stacked layout at a width breakpoint, for instance when
 * a tablet is turned. The timer of the question runs on at the server meanwhile, so a draft lost then would also be sent as an
 * empty answer once the time runs out. Question ids are unique across all checks, so a draft is never given back for another
 * question, nor to another student.
 */
@Service()
export class MetisAnswerDrafts {
    /** At most one draft per exercise: only one question of a check is open at a time. */
    private readonly drafts = new Map<number, KeptDraft>();

    /**
     * The draft kept for the open question of an exercise. A draft kept for another question of the exercise is dropped:
     * that question is no longer open, so its draft cannot be sent any more.
     * @param exerciseId the exercise of the check
     * @param questionId the question that the check shows now
     * @returns the kept draft, or an empty one
     */
    restore(exerciseId: number, questionId: number): string {
        const kept = this.drafts.get(exerciseId);
        if (kept?.questionId === questionId) {
            return kept.text;
        }
        this.drafts.delete(exerciseId);
        return '';
    }

    /**
     * Keeps the draft of the open question of an exercise in place of any other; an empty draft leaves nothing to keep.
     * @param exerciseId the exercise of the check
     * @param questionId the question that the check shows
     * @param text the draft as it is now
     */
    keep(exerciseId: number, questionId: number, text: string): void {
        if (text) {
            this.drafts.set(exerciseId, { questionId, text });
        } else {
            this.drafts.delete(exerciseId);
        }
    }

    /**
     * Drops the draft of an exercise once its check shows no open question.
     * @param exerciseId the exercise of the check
     */
    drop(exerciseId: number): void {
        this.drafts.delete(exerciseId);
    }
}
