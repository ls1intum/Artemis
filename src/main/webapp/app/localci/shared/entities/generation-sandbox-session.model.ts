export type GenerationSandboxRole = 'AUTHORING' | 'VERIFICATION';
export type GenerationMode = 'GENERATE' | 'ADAPT';

export interface GenerationSandboxSession {
    sessionId: string;
    role: GenerationSandboxRole;
    jobId: string;
    exerciseId: number;
    courseId?: number;
    userLogin: string;
    mode: GenerationMode;
    startedAt: string;
    lastActivityAt: string;
    reservedSlots: number;
}

export interface GenerationSandboxJob {
    jobId: string;
    exerciseId: number;
    courseId?: number;
    userLogin: string;
    mode: GenerationMode;
    startedAt: string;
    lastActivityAt: string;
    reservedSlots: number;
    sessions: GenerationSandboxSession[];
}

export function groupGenerationSandboxSessions(sessions: GenerationSandboxSession[]): GenerationSandboxJob[] {
    const jobs = new Map<string, GenerationSandboxJob>();
    for (const session of sessions) {
        const job = jobs.get(session.jobId);
        if (job) {
            job.sessions.push(session);
            job.reservedSlots += session.reservedSlots;
            if (Date.parse(session.lastActivityAt) > Date.parse(job.lastActivityAt)) {
                job.lastActivityAt = session.lastActivityAt;
            }
            if (Date.parse(session.startedAt) < Date.parse(job.startedAt)) {
                job.startedAt = session.startedAt;
            }
        } else {
            jobs.set(session.jobId, { ...session, sessions: [session] });
        }
    }
    return [...jobs.values()].sort((left, right) => Date.parse(right.startedAt) - Date.parse(left.startedAt));
}
