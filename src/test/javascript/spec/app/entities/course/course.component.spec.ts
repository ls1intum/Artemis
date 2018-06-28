/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { CourseComponent } from '../../../../../../main/webapp/app/entities/course/course.component';
import { CourseService } from '../../../../../../main/webapp/app/entities/course/course.service';
import { Course } from '../../../../../../main/webapp/app/entities/course/course.model';

describe('Component Tests', () => {

    describe('Course Management Component', () => {
        let comp: CourseComponent;
        let fixture: ComponentFixture<CourseComponent>;
        let service: CourseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [CourseComponent],
                providers: [
                    CourseService
                ]
            })
            .overrideTemplate(CourseComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(CourseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(CourseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new Course(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.courses[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
