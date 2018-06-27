/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { CourseDetailComponent } from '../../../../../../main/webapp/app/entities/course/course-detail.component';
import { CourseService } from '../../../../../../main/webapp/app/entities/course/course.service';
import { Course } from '../../../../../../main/webapp/app/entities/course/course.model';

describe('Component Tests', () => {

    describe('Course Management Detail Component', () => {
        let comp: CourseDetailComponent;
        let fixture: ComponentFixture<CourseDetailComponent>;
        let service: CourseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [CourseDetailComponent],
                providers: [
                    CourseService
                ]
            })
            .overrideTemplate(CourseDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(CourseDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(CourseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new Course(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.course).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
