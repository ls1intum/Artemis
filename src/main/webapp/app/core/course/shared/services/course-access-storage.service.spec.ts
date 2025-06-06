import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LocalStorageService, provideNgxWebstorage, withLocalStorage, withNgxWebstorageConfig, withSessionStorage } from 'ngx-webstorage';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';
import { MockLocalStorageService } from 'test/helpers/mocks/service/mock-local-storage.service';

describe('CourseAccessStorageService', () => {
    let service: CourseAccessStorageService;
    let localStorage: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                CourseAccessStorageService,
                provideNgxWebstorage(withNgxWebstorageConfig({ separator: ':', caseSensitive: true }), withLocalStorage(), withSessionStorage()),
                {
                    provide: LocalStorageService,
                    useClass: MockLocalStorageService,
                },
            ],
        });
        service = TestBed.inject(CourseAccessStorageService);
        localStorage = TestBed.inject(LocalStorageService);
    });

    it('should store accessed course', () => {
        const courseId = 123;
        service.onCourseAccessed(courseId, CourseAccessStorageService.STORAGE_KEY, CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW);
        const courseAccessMap = localStorage.retrieve('artemis.courseAccess');
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
