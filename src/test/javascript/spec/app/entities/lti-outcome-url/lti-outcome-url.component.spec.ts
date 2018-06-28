/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { LtiOutcomeUrlComponent } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url.component';
import { LtiOutcomeUrlService } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url.service';
import { LtiOutcomeUrl } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url.model';

describe('Component Tests', () => {

    describe('LtiOutcomeUrl Management Component', () => {
        let comp: LtiOutcomeUrlComponent;
        let fixture: ComponentFixture<LtiOutcomeUrlComponent>;
        let service: LtiOutcomeUrlService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [LtiOutcomeUrlComponent],
                providers: [
                    LtiOutcomeUrlService
                ]
            })
            .overrideTemplate(LtiOutcomeUrlComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(LtiOutcomeUrlComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiOutcomeUrlService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new LtiOutcomeUrl(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.ltiOutcomeUrls[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
