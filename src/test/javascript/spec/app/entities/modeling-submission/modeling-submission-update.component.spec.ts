/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { ModelingSubmissionUpdateComponent } from 'app/entities/modeling-submission/modeling-submission-update.component';
import { ModelingSubmissionService } from 'app/entities/modeling-submission/modeling-submission.service';
import { ModelingSubmission } from 'app/shared/model/modeling-submission.model';

describe('Component Tests', () => {
    describe('ModelingSubmission Management Update Component', () => {
        let comp: ModelingSubmissionUpdateComponent;
        let fixture: ComponentFixture<ModelingSubmissionUpdateComponent>;
        let service: ModelingSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ModelingSubmissionUpdateComponent]
            })
                .overrideTemplate(ModelingSubmissionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ModelingSubmissionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingSubmissionService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ModelingSubmission(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.modelingSubmission = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ModelingSubmission();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.modelingSubmission = entity;
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
