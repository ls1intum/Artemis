/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { AnswerOptionDetailComponent } from '../../../../../../main/webapp/app/entities/answer-option/answer-option-detail.component';
import { AnswerOptionService } from '../../../../../../main/webapp/app/entities/answer-option/answer-option.service';
import { AnswerOption } from '../../../../../../main/webapp/app/entities/answer-option/answer-option.model';

describe('Component Tests', () => {

    describe('AnswerOption Management Detail Component', () => {
        let comp: AnswerOptionDetailComponent;
        let fixture: ComponentFixture<AnswerOptionDetailComponent>;
        let service: AnswerOptionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [AnswerOptionDetailComponent],
                providers: [
                    AnswerOptionService
                ]
            })
            .overrideTemplate(AnswerOptionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(AnswerOptionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerOptionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new AnswerOption(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.answerOption).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
