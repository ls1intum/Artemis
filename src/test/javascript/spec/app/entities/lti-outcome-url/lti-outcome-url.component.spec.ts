/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { LtiOutcomeUrlComponent } from 'app/entities/lti-outcome-url/lti-outcome-url.component';
import { LtiOutcomeUrlService } from 'app/entities/lti-outcome-url/lti-outcome-url.service';
import { LtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';

describe('Component Tests', () => {
    describe('LtiOutcomeUrl Management Component', () => {
        let comp: LtiOutcomeUrlComponent;
        let fixture: ComponentFixture<LtiOutcomeUrlComponent>;
        let service: LtiOutcomeUrlService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [LtiOutcomeUrlComponent],
                providers: []
            })
                .overrideTemplate(LtiOutcomeUrlComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(LtiOutcomeUrlComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiOutcomeUrlService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new LtiOutcomeUrl(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.ltiOutcomeUrls[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
