/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { TextSubmissionUpdateComponent } from 'app/entities/text-submission/text-submission-update.component';
import { TextSubmissionService } from 'app/entities/text-submission/text-submission.service';
import { TextSubmission } from 'app/shared/model/text-submission.model';

describe('Component Tests', () => {
    describe('TextSubmission Management Update Component', () => {
        let comp: TextSubmissionUpdateComponent;
        let fixture: ComponentFixture<TextSubmissionUpdateComponent>;
        let service: TextSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [TextSubmissionUpdateComponent]
            })
                .overrideTemplate(TextSubmissionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(TextSubmissionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextSubmissionService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new TextSubmission(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.textSubmission = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );

            it(
                'Should call create service on save for new entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new TextSubmission();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.textSubmission = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.create).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );
        });
    });
});
