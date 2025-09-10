import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

describe('CourseAccessStorageService', () => {
    let service: CourseAccessStorageService;
    let localStorageService: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [CourseAccessStorageService],
        });
        service = TestBed.inject(CourseAccessStorageService);
        localStorageService = TestBed.inject(LocalStorageService);
    });

    it('should store accessed course', () => {
        const courseId = 123;
        service.onCourseAccessed(courseId, CourseAccessStorageService.STORAGE_KEY, CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW);
        const courseAccessMap = localStorageService.retrieve<{ [key: number]: number }>(CourseAccessStorageService.STORAGE_KEY);
        expect(courseAccessMap).toHaveProperty(courseId.toString());
    });

    it('should retrieve last accessed courses and remove older courses', fakeAsync(() => {
        const courseIds = [123, 456, 789, 101112, 7494];
        courseIds.forEach((courseId) => {
            service.onCourseAccessed(courseId, CourseAccessStorageService.STORAGE_KEY, CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW);
            tick(10); // Wait 10ms to ensure that the timestamp is different for each course
        });
        const lastAccessedCourses = service.getLastAccessedCourses(CourseAccessStorageService.STORAGE_KEY);
        expect(lastAccessedCourses).toEqual(courseIds.reverse().slice(0, 3));
    }));
});
