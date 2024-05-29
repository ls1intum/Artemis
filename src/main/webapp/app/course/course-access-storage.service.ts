import { Injectable } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';

@Injectable({
    providedIn: 'root',
})
export class CourseAccessStorageService {
    private static readonly STORAGE_KEY = 'artemis.courseAccess';
    private static readonly STORAGE_KEY_DROPDOWN = 'artemis.courseAccessDropdown';

    constructor(private localStorage: LocalStorageService) {}

    onCourseAccessed(courseId: number): void {
        const courseAccessMap: { [key: number]: number } = this.localStorage.retrieve(CourseAccessStorageService.STORAGE_KEY) || {};

        courseAccessMap[courseId] = Date.now();

        if (Object.keys(courseAccessMap).length > 3) {
            const oldestEntry = Object.entries(courseAccessMap).reduce((prev, curr) => (prev[1] < curr[1] ? prev : curr));
            delete courseAccessMap[oldestEntry[0]];
        }

        this.localStorage.store(CourseAccessStorageService.STORAGE_KEY, courseAccessMap);
    }

    getLastAccessedCourses(): number[] {
        const courseAccessMap: { [key: number]: number } = this.localStorage.retrieve(CourseAccessStorageService.STORAGE_KEY) || {};

        return Object.entries(courseAccessMap)
            .sort((a, b) => b[1] - a[1])
            .map((entry) => Number(entry[0]));
    }

    onCourseAccessedDropdown(courseId: number): void {
        const courseAccessMap: { [key: number]: number } = this.localStorage.retrieve(CourseAccessStorageService.STORAGE_KEY_DROPDOWN) || {};

        courseAccessMap[courseId] = Date.now();

        if (Object.keys(courseAccessMap).length > 6) {
            const oldestEntry = Object.entries(courseAccessMap).reduce((prev, curr) => (prev[1] < curr[1] ? prev : curr));
            delete courseAccessMap[oldestEntry[0]];
        }

        this.localStorage.store(CourseAccessStorageService.STORAGE_KEY_DROPDOWN, courseAccessMap);
    }

    getLastAccessedCoursesDropdown(): number[] {
        const courseAccessMap: { [key: number]: number } = this.localStorage.retrieve(CourseAccessStorageService.STORAGE_KEY_DROPDOWN) || {};

        return Object.entries(courseAccessMap)
            .sort((a, b) => b[1] - a[1])
            .map((entry) => Number(entry[0]));
    }
}
