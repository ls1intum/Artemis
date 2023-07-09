import { BaseEntity } from 'app/shared/model/base-entity';

export class LspStatus implements BaseEntity {
    id?: number;
    url?: string;
    healthy?: boolean;
    activeSessions?: number;
    loadAvg1: number;
    loadAvg5: number;
    loadAvg15: number;
    totalMem: number;
    freeMem: number;
    memUsage: number;
    cpuUsage: number;
    timestamp?: Date;
    paused = false;
}
