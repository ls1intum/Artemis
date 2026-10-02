import { ScienceEventType } from 'app/foundation/science/science.model';

export interface SelectableCourse {
    id: number;
    title?: string;
    shortName?: string;
    semester?: string;
}

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
    filter?: ScienceResearchExportFilter;
    fileChecksum?: string;
}

interface ScienceResearchExportFilter {
    courseIds?: number[];
    dateFrom?: string;
    dateTo?: string;
    eventTypes?: ScienceEventType[];
}

export interface ScienceResearchExportRequest {
    courseIds: number[];
    from?: string;
    to?: string;
    eventTypes?: ScienceEventType[];
    purpose: string;
}
