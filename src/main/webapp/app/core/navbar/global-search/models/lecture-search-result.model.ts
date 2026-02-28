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
}

export interface LectureSearchResult {
    course: CourseInfo;
    lecture: LectureInfo;
    lectureUnit: LectureUnitInfo;
    snippet?: string;
}
