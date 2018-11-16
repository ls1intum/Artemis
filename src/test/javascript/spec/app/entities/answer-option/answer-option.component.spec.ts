/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { AnswerOptionComponent } from 'app/entities/answer-option/answer-option.component';
import { AnswerOptionService } from 'app/entities/answer-option/answer-option.service';
import { AnswerOption } from 'app/shared/model/answer-option.model';

describe('Component Tests', () => {
    describe('AnswerOption Management Component', () => {
        let comp: AnswerOptionComponent;
        let fixture: ComponentFixture<AnswerOptionComponent>;
        let service: AnswerOptionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [AnswerOptionComponent],
                providers: []
            })
                .overrideTemplate(AnswerOptionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(AnswerOptionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerOptionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new AnswerOption(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.answerOptions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
