/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { CourseComponent } from 'app/entities/course/course.component';
import { CourseService } from 'app/entities/course/course.service';
import { Course } from 'app/shared/model/course.model';

describe('Component Tests', () => {
    describe('Course Management Component', () => {
        let comp: CourseComponent;
        let fixture: ComponentFixture<CourseComponent>;
        let service: CourseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [CourseComponent],
                providers: []
            })
                .overrideTemplate(CourseComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(CourseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(CourseService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new Course(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.courses[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
