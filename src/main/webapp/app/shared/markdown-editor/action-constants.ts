import { ReferenceType } from 'app/shared/metis/metis.util';

export type ValueItem = {
    id: string;
    value: string;
    type?: string;
    elements?: ValueItem[];
    attachmentUnits?: ValueItem[];
};

// TODO: Remove and rename file
export type SlideItem = {
    id: string;
    slideImagePath: string;
    slideNumber: number;
    courseArtifactType: ReferenceType;
};
