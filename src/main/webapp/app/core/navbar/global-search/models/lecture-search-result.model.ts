export interface CourseInfo {
    id: number;
    name: string;
}

export interface LectureInfo {
    id: number;
    name: string;
}

export interface LectureUnitInfo {
    id: number;
    name: string;
    link: string;
    pageNumber: number;
    sourceType: string;
    queryParams: Record<string, string | number>;
    displayMeta?: string;
}

export interface LectureSearchResult {
    course: CourseInfo;
    lecture: LectureInfo;
    lectureUnit: LectureUnitInfo;
    snippet?: string;
}
