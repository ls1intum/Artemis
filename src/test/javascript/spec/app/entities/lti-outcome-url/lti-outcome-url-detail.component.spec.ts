/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { LtiOutcomeUrlDetailComponent } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url-detail.component';
import { LtiOutcomeUrlService } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url.service';
import { LtiOutcomeUrl } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url.model';

describe('Component Tests', () => {

    describe('LtiOutcomeUrl Management Detail Component', () => {
        let comp: LtiOutcomeUrlDetailComponent;
        let fixture: ComponentFixture<LtiOutcomeUrlDetailComponent>;
        let service: LtiOutcomeUrlService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [LtiOutcomeUrlDetailComponent],
                providers: [
                    LtiOutcomeUrlService
                ]
            })
            .overrideTemplate(LtiOutcomeUrlDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(LtiOutcomeUrlDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiOutcomeUrlService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new LtiOutcomeUrl(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.ltiOutcomeUrl).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
