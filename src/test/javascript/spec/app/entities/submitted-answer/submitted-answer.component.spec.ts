/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { SubmittedAnswerComponent } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer.component';
import { SubmittedAnswerService } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer.service';
import { SubmittedAnswer } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer.model';

describe('Component Tests', () => {

    describe('SubmittedAnswer Management Component', () => {
        let comp: SubmittedAnswerComponent;
        let fixture: ComponentFixture<SubmittedAnswerComponent>;
        let service: SubmittedAnswerService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [SubmittedAnswerComponent],
                providers: [
                    SubmittedAnswerService
                ]
            })
            .overrideTemplate(SubmittedAnswerComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(SubmittedAnswerComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmittedAnswerService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new SubmittedAnswer(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.submittedAnswers[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
