export interface CourseForArchiveDTO {
    id: number;
    title: string;
    semester?: string;
    color: string;
    icon: string;
    testCourse: boolean;
    canManage: boolean;
}
