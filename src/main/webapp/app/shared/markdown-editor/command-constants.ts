export type ValueItem = {
    id: string;
    value: string;
    type?: CourseArtifactType;
    elements?: ValueItem[];
};

export enum CourseArtifactType {
    EXERCISE = 'Exercise',
    LECTURE = 'Lecture',
    ATTACHMENT = 'Attachment',
}
