/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { LtiOutcomeUrlUpdateComponent } from 'app/entities/lti-outcome-url/lti-outcome-url-update.component';
import { LtiOutcomeUrlService } from 'app/entities/lti-outcome-url/lti-outcome-url.service';
import { LtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';

describe('Component Tests', () => {
    describe('LtiOutcomeUrl Management Update Component', () => {
        let comp: LtiOutcomeUrlUpdateComponent;
        let fixture: ComponentFixture<LtiOutcomeUrlUpdateComponent>;
        let service: LtiOutcomeUrlService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [LtiOutcomeUrlUpdateComponent]
            })
                .overrideTemplate(LtiOutcomeUrlUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(LtiOutcomeUrlUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiOutcomeUrlService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new LtiOutcomeUrl(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.ltiOutcomeUrl = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new LtiOutcomeUrl();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.ltiOutcomeUrl = entity;
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
