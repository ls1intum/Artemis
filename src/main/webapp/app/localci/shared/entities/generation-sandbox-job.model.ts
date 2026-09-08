export type GenerationMode = 'GENERATE' | 'ADAPT';

export interface GenerationSandboxJob {
    sessionId: string;
    jobId: string;
    exerciseId: number;
    exerciseTitle: string;
    courseId?: number;
    userLogin: string;
    mode: GenerationMode;
    startedAt: string;
    lastActivityAt: string;
    agentName?: string;
    stale?: boolean;
}
