/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { SubmittedAnswerDetailComponent } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer-detail.component';
import { SubmittedAnswerService } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer.service';
import { SubmittedAnswer } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer.model';

describe('Component Tests', () => {

    describe('SubmittedAnswer Management Detail Component', () => {
        let comp: SubmittedAnswerDetailComponent;
        let fixture: ComponentFixture<SubmittedAnswerDetailComponent>;
        let service: SubmittedAnswerService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [SubmittedAnswerDetailComponent],
                providers: [
                    SubmittedAnswerService
                ]
            })
            .overrideTemplate(SubmittedAnswerDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(SubmittedAnswerDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmittedAnswerService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new SubmittedAnswer(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.submittedAnswer).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
