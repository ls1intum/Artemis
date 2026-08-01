import { ScienceEventType } from 'app/foundation/science/science.model';

export interface ScienceEnabledCourse {
    courseId: number;
    courseTitle?: string;
    courseShortName?: string;
    active: boolean;
    createdDate?: string;
    createdBy?: string;
    lastModifiedDate?: string;
    lastModifiedBy?: string;
}

export interface ScienceResearchExportAudit {
    id: number;
    createdDate?: string;
    createdBy?: string;
    purpose?: string;
    courseFilter?: string;
    dateFrom?: string;
    dateTo?: string;
    eventTypes?: string;
    fileChecksum?: string;
}

export interface ScienceResearchExportRequest {
    courseIds: number[];
    from?: string;
    to?: string;
    eventTypes?: ScienceEventType[];
    purpose: string;
}
