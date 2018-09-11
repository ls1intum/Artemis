/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { LtiUserIdUpdateComponent } from 'app/entities/lti-user-id/lti-user-id-update.component';
import { LtiUserIdService } from 'app/entities/lti-user-id/lti-user-id.service';
import { LtiUserId } from 'app/shared/model/lti-user-id.model';

describe('Component Tests', () => {
    describe('LtiUserId Management Update Component', () => {
        let comp: LtiUserIdUpdateComponent;
        let fixture: ComponentFixture<LtiUserIdUpdateComponent>;
        let service: LtiUserIdService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [LtiUserIdUpdateComponent]
            })
                .overrideTemplate(LtiUserIdUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(LtiUserIdUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiUserIdService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new LtiUserId(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.ltiUserId = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new LtiUserId();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.ltiUserId = entity;
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
