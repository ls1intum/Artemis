/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { CourseUpdateComponent } from 'app/entities/course/course-update.component';
import { CourseService } from 'app/entities/course/course.service';
import { Course } from 'app/shared/model/course.model';

describe('Component Tests', () => {
    describe('Course Management Update Component', () => {
        let comp: CourseUpdateComponent;
        let fixture: ComponentFixture<CourseUpdateComponent>;
        let service: CourseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [CourseUpdateComponent]
            })
                .overrideTemplate(CourseUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(CourseUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(CourseService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new Course(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.course = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new Course();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.course = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));
        });
    });
});
