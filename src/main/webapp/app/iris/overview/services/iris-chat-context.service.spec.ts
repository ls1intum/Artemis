import { beforeEach, describe, expect, it } from 'vitest';
import { IrisChatContextService } from 'app/iris/overview/services/iris-chat-context.service';
import { ChatServiceMode, SessionContext, sameSessionContext } from 'app/iris/shared/entities/iris-session-context.model';

describe('IrisChatContextService', () => {
    // The service only uses signal()/computed() (no effect), so it can be unit-tested without an injection
    // context. A fresh instance per test keeps the (committed, pending, page) state isolated.
    let service: IrisChatContextService;

    const course: SessionContext = { mode: ChatServiceMode.COURSE, entityId: 1 };
    const lecture: SessionContext = { mode: ChatServiceMode.LECTURE, entityId: 7 };
    const lectureNamed: SessionContext = { mode: ChatServiceMode.LECTURE, entityId: 7, entityName: 'Intro Lecture' };
    const exercise: SessionContext = { mode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 42 };
    const tutorSuggestion: SessionContext = { mode: ChatServiceMode.TUTOR_SUGGESTION, entityId: 99 };

    beforeEach(() => {
        service = new IrisChatContextService();
    });

    it('should start with all contexts undefined', () => {
        expect(service.committed()).toBeUndefined();
        expect(service.pending()).toBeUndefined();
        expect(service.page()).toBeUndefined();
        expect(service.display()).toBeUndefined();
    });

    describe('setPageContext', () => {
        it('should set the page signal', () => {
            service.setPageContext(lecture);

            expect(service.page()).toEqual(lecture);
        });
    });

    describe('display', () => {
        it('should prefer pending over committed', () => {
            service.adoptServerContext(course);
            service.stagePending(lecture);

            expect(service.display()).toEqual(lecture);
        });

        it('should fall back to committed when nothing is pending', () => {
            service.adoptServerContext(course);

            expect(service.display()).toEqual(course);
        });
    });

    describe('stagePending', () => {
        it('should stage a context that differs from the committed one', () => {
            service.adoptServerContext(course);

            service.stagePending(lecture);

            expect(service.pending()).toEqual(lecture);
        });

        it('should clear pending when staging the already-committed context', () => {
            service.adoptServerContext(course);
            service.stagePending(lecture);
            expect(service.pending()).toEqual(lecture);

            service.stagePending(course);

            expect(service.pending()).toBeUndefined();
        });

        it('should ignore entityName when comparing against the committed context', () => {
            service.adoptServerContext(lecture);

            // Same mode + entityId as committed (only the name differs) → no spurious pending override.
            service.stagePending(lectureNamed);

            expect(service.pending()).toBeUndefined();
        });

        it('should keep the current pending when the same option is re-staged', () => {
            service.adoptServerContext(course);
            service.stagePending(lecture);

            service.stagePending({ ...lecture });

            expect(service.pending()).toEqual(lecture);
        });
    });

    describe('commitSentContext', () => {
        it('should promote the sent context to committed and clear pending when nothing newer was staged', () => {
            service.adoptServerContext(course);
            service.stagePending(lecture);

            service.commitSentContext(lecture);

            expect(service.committed()).toEqual(lecture);
            expect(service.pending()).toBeUndefined();
        });

        it('should commit the sent context but preserve a newer pending staged while the request was in flight', () => {
            // User sends with `lecture` staged, then picks `exercise` before the response arrives. The
            // server persisted `lecture`, so committed must follow it — and `exercise` must survive as
            // pending so the next sendMessage applies it (no client/server desync).
            service.adoptServerContext(course);
            service.stagePending(lecture);
            service.stagePending(exercise);

            service.commitSentContext(lecture);

            expect(service.committed()).toEqual(lecture);
            expect(service.pending()).toEqual(exercise);
        });

        it('should clear pending when the newer selection reverts to the sent context', () => {
            // Staged `lecture`, sent it, then re-picked `lecture` again: nothing newer remains, so the
            // override is cleared (matching the sameSessionContext check, which ignores entityName).
            service.adoptServerContext(course);
            service.stagePending(lecture);

            service.commitSentContext(lectureNamed);

            expect(service.committed()).toEqual(lectureNamed);
            expect(service.pending()).toBeUndefined();
        });
    });

    describe('adoptServerContext', () => {
        it('should set committed and discard any in-flight pending', () => {
            service.adoptServerContext(course);
            service.stagePending(lecture);

            service.adoptServerContext(exercise);

            expect(service.committed()).toEqual(exercise);
            expect(service.pending()).toBeUndefined();
        });

        it('should auto-stage the page context when on a non-course page that differs from the server', () => {
            service.setPageContext(lecture);

            // Server fell back to the course session, but the user is on a lecture page: stage the lecture so
            // the chip reflects intent and the next sendMessage commits it.
            service.adoptServerContext(course);

            expect(service.committed()).toEqual(course);
            expect(service.pending()).toEqual(lecture);
        });

        it('should not auto-stage when the page context equals the server context', () => {
            service.setPageContext(lecture);

            service.adoptServerContext(lecture);

            expect(service.pending()).toBeUndefined();
        });

        it('should not auto-stage when the page is a course page', () => {
            service.setPageContext(course);

            service.adoptServerContext(course);

            expect(service.pending()).toBeUndefined();
        });

        it('should not auto-stage when the page is a tutor-suggestion page', () => {
            // Tutor-suggestion sessions come back with mode unset (serverCtx undefined) and are not
            // IrisChatSessions: the server rejects a pending context change for them, so the page context
            // must never be auto-staged — otherwise the next sendMessage would 400.
            service.setPageContext(tutorSuggestion);

            service.adoptServerContext(undefined);

            expect(service.pending()).toBeUndefined();
        });

        it('should not auto-stage when there is no page context', () => {
            service.adoptServerContext(course);

            expect(service.pending()).toBeUndefined();
        });
    });

    describe('reset', () => {
        it('should clear committed, pending and page', () => {
            service.setPageContext(lecture);
            service.adoptServerContext(course);
            service.stagePending(exercise);

            service.reset();

            expect(service.committed()).toBeUndefined();
            expect(service.pending()).toBeUndefined();
            expect(service.page()).toBeUndefined();
        });
    });

    describe('sameSessionContext', () => {
        it('should treat identical references as equal', () => {
            expect(sameSessionContext(lecture, lecture)).toBe(true);
        });

        it('should treat two undefined contexts as equal', () => {
            expect(sameSessionContext(undefined, undefined)).toBe(true);
        });

        it('should treat one undefined context as not equal', () => {
            expect(sameSessionContext(lecture, undefined)).toBe(false);
            expect(sameSessionContext(undefined, lecture)).toBe(false);
        });

        it('should ignore entityName', () => {
            expect(sameSessionContext(lecture, lectureNamed)).toBe(true);
        });

        it('should compare mode and entityId', () => {
            expect(sameSessionContext(lecture, { mode: ChatServiceMode.LECTURE, entityId: 8 })).toBe(false);
            expect(sameSessionContext(lecture, { mode: ChatServiceMode.COURSE, entityId: 7 })).toBe(false);
        });
    });
});
