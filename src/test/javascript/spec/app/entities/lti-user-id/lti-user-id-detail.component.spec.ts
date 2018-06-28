/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { LtiUserIdDetailComponent } from '../../../../../../main/webapp/app/entities/lti-user-id/lti-user-id-detail.component';
import { LtiUserIdService } from '../../../../../../main/webapp/app/entities/lti-user-id/lti-user-id.service';
import { LtiUserId } from '../../../../../../main/webapp/app/entities/lti-user-id/lti-user-id.model';

describe('Component Tests', () => {

    describe('LtiUserId Management Detail Component', () => {
        let comp: LtiUserIdDetailComponent;
        let fixture: ComponentFixture<LtiUserIdDetailComponent>;
        let service: LtiUserIdService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [LtiUserIdDetailComponent],
                providers: [
                    LtiUserIdService
                ]
            })
            .overrideTemplate(LtiUserIdDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(LtiUserIdDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiUserIdService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new LtiUserId(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.ltiUserId).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
