import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LocalStorageService, NgxWebstorageModule } from 'ngx-webstorage';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';
import { MockLocalStorageService } from '../helpers/mocks/service/mock-local-storage.service';

describe('CourseAccessStorageService', () => {
    let service: CourseAccessStorageService;
    let localStorage: LocalStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgxWebstorageModule.forRoot()],
            providers: [
                CourseAccessStorageService,
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
        service.onCourseAccessed(courseId);
        const courseAccessMap = localStorage.retrieve('artemis.courseAccess');
        expect(courseAccessMap).toHaveProperty(courseId.toString());
    });

    it('should retrieve last accessed courses and remove older courses', fakeAsync(() => {
        const courseIds = [123, 456, 789, 101112, 7494];
        courseIds.forEach((courseId) => {
            service.onCourseAccessed(courseId);
            tick(10); // Wait 10ms to ensure that the timestamp is different for each course
        });
        const lastAccessedCourses = service.getLastAccessedCourses();
        expect(lastAccessedCourses).toEqual(courseIds.reverse().slice(0, 3));
    }));
});
