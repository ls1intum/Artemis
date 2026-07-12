import { describe, expect, it } from 'vitest';
import { GenerationSandboxSession, groupGenerationSandboxSessions } from 'app/localci/shared/entities/generation-sandbox-session.model';

describe('groupGenerationSandboxSessions', () => {
    it('uses the earliest session start regardless of response order', () => {
        const common = {
            jobId: 'job-1',
            exerciseId: 42,
            exerciseTitle: 'Concurrency Lab',
            userLogin: 'instructor',
            mode: 'GENERATE',
            reservedSlots: 1,
        } as const;
        const sessions: GenerationSandboxSession[] = [
            { ...common, sessionId: 'verification', role: 'VERIFICATION', startedAt: '2026-07-12T09:00:00.100Z', lastActivityAt: '2026-07-12T09:06:00.100Z' },
            { ...common, sessionId: 'authoring', role: 'AUTHORING', startedAt: '2026-07-12T09:00:00Z', lastActivityAt: '2026-07-12T09:06:00Z' },
        ];

        expect(groupGenerationSandboxSessions(sessions)[0]).toEqual(
            expect.objectContaining({ startedAt: '2026-07-12T09:00:00Z', lastActivityAt: '2026-07-12T09:06:00.100Z', reservedSlots: 2 }),
        );
    });
});
