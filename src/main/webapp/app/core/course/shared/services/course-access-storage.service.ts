import { Injectable, inject } from '@angular/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

@Injectable({
    providedIn: 'root',
})
export class CourseAccessStorageService {
    private localStorageService = inject(LocalStorageService);

    public static readonly STORAGE_KEY = 'artemis.courseAccess';
    public static readonly STORAGE_KEY_DROPDOWN = 'artemis.courseAccessDropdown';
    public static readonly MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW = 3;
    // Maximum number of recently accessed courses displayed in the dropdown, including the current course. The current course will be removed before displaying the dropdown so only 6 - 1 courses will be displayed in the dropdown.
    public static readonly MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_DROPDOWN = 6;

    onCourseAccessed(courseId: number, storageKey: string, maxAccessedCourses: number): void {
        const courseAccessMap = this.localStorageService.retrieve<{ [key: number]: number }>(storageKey) ?? {};

        courseAccessMap[courseId] = Date.now();

        if (Object.keys(courseAccessMap).length > maxAccessedCourses) {
            const oldestEntry = Object.entries(courseAccessMap).reduce((prev, curr) => (prev[1] < curr[1] ? prev : curr));
            delete courseAccessMap[Number(oldestEntry[0])];
        }

        this.localStorageService.store<{ [key: number]: number }>(storageKey, courseAccessMap);
    }

    getLastAccessedCourses(storageKey: string): number[] {
        const courseAccessMap = this.localStorageService.retrieve<{ [key: number]: number }>(storageKey) ?? {};

        return Object.entries(courseAccessMap)
            .sort((a, b) => b[1] - a[1])
            .map((entry) => Number(entry[0]));
    }
}
