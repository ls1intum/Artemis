/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { SubmissionUpdateComponent } from 'app/entities/submission/submission-update.component';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { Submission } from 'app/shared/model/submission.model';

describe('Component Tests', () => {
    describe('Submission Management Update Component', () => {
        let comp: SubmissionUpdateComponent;
        let fixture: ComponentFixture<SubmissionUpdateComponent>;
        let service: SubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [SubmissionUpdateComponent]
            })
                .overrideTemplate(SubmissionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(SubmissionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmissionService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new Submission(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.submission = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new Submission();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.submission = entity;
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
