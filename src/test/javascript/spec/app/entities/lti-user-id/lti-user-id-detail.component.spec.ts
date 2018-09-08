/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { LtiUserIdDetailComponent } from 'app/entities/lti-user-id/lti-user-id-detail.component';
import { LtiUserId } from 'app/shared/model/lti-user-id.model';

describe('Component Tests', () => {
    describe('LtiUserId Management Detail Component', () => {
        let comp: LtiUserIdDetailComponent;
        let fixture: ComponentFixture<LtiUserIdDetailComponent>;
        const route = ({ data: of({ ltiUserId: new LtiUserId(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [LtiUserIdDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(LtiUserIdDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(LtiUserIdDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.ltiUserId).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
