import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

import { ArtemisTestModule } from '../../test.module';
import { CourseDetailComponent } from 'app/course/manage/course-detail.component';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../../helpers/mocks/service/mock-course-management.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';

describe('Course Management Detail Component', () => {
    let comp: CourseDetailComponent;
    let fixture: ComponentFixture<CourseDetailComponent>;
    let service: CourseManagementService;

    const course: Course = { id: 123 } as Course;
    const route = ({ params: of({ courseId: course.id }) } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseDetailComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
            ],
        })
            .overrideTemplate(CourseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(CourseDetailComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(CourseManagementService);
    });

    describe('OnInit', () => {
        it('Should call load on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'find').and.returnValue(
                of(
                    new HttpResponse({
                        body: course,
                        headers,
                    }),
                ),
            );

            // WHEN
            fixture.detectChanges();
            comp.ngOnInit();

            // THEN
            expect(service.find).toHaveBeenCalled();
            expect(comp.course).toEqual(jasmine.objectContaining({ id: course.id }));
        });
    });
});
