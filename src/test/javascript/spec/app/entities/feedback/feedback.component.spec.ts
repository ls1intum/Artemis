/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { FeedbackComponent } from '../../../../../../main/webapp/app/entities/feedback/feedback.component';
import { FeedbackService } from '../../../../../../main/webapp/app/entities/feedback/feedback.service';
import { Feedback } from '../../../../../../main/webapp/app/entities/feedback/feedback.model';

describe('Component Tests', () => {

    describe('Feedback Management Component', () => {
        let comp: FeedbackComponent;
        let fixture: ComponentFixture<FeedbackComponent>;
        let service: FeedbackService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [FeedbackComponent],
                providers: [
                    FeedbackService
                ]
            })
            .overrideTemplate(FeedbackComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(FeedbackComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FeedbackService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new Feedback(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.feedbacks[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
