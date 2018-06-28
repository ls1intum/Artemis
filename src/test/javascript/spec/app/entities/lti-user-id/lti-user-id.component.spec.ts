/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { LtiUserIdComponent } from '../../../../../../main/webapp/app/entities/lti-user-id/lti-user-id.component';
import { LtiUserIdService } from '../../../../../../main/webapp/app/entities/lti-user-id/lti-user-id.service';
import { LtiUserId } from '../../../../../../main/webapp/app/entities/lti-user-id/lti-user-id.model';

describe('Component Tests', () => {

    describe('LtiUserId Management Component', () => {
        let comp: LtiUserIdComponent;
        let fixture: ComponentFixture<LtiUserIdComponent>;
        let service: LtiUserIdService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [LtiUserIdComponent],
                providers: [
                    LtiUserIdService
                ]
            })
            .overrideTemplate(LtiUserIdComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(LtiUserIdComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiUserIdService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new LtiUserId(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.ltiUserIds[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
