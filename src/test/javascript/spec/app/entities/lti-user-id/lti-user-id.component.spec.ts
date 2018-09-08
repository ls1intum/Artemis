/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { LtiUserIdComponent } from 'app/entities/lti-user-id/lti-user-id.component';
import { LtiUserIdService } from 'app/entities/lti-user-id/lti-user-id.service';
import { LtiUserId } from 'app/shared/model/lti-user-id.model';

describe('Component Tests', () => {
    describe('LtiUserId Management Component', () => {
        let comp: LtiUserIdComponent;
        let fixture: ComponentFixture<LtiUserIdComponent>;
        let service: LtiUserIdService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [LtiUserIdComponent],
                providers: []
            })
                .overrideTemplate(LtiUserIdComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(LtiUserIdComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiUserIdService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new LtiUserId(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.ltiUserIds[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
