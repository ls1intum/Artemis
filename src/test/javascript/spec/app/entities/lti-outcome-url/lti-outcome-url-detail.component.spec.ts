/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { LtiOutcomeUrlDetailComponent } from 'app/entities/lti-outcome-url/lti-outcome-url-detail.component';
import { LtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';

describe('Component Tests', () => {
    describe('LtiOutcomeUrl Management Detail Component', () => {
        let comp: LtiOutcomeUrlDetailComponent;
        let fixture: ComponentFixture<LtiOutcomeUrlDetailComponent>;
        const route = ({ data: of({ ltiOutcomeUrl: new LtiOutcomeUrl(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [LtiOutcomeUrlDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(LtiOutcomeUrlDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(LtiOutcomeUrlDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.ltiOutcomeUrl).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
