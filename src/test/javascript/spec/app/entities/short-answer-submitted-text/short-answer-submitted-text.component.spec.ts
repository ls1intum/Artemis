/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSubmittedTextComponent } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.component';
import { ShortAnswerSubmittedTextService } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.service';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.model';

describe('Component Tests', () => {
    describe('ShortAnswerSubmittedText Management Component', () => {
        let comp: ShortAnswerSubmittedTextComponent;
        let fixture: ComponentFixture<ShortAnswerSubmittedTextComponent>;
        let service: ShortAnswerSubmittedTextService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSubmittedTextComponent],
                providers: []
            })
                .overrideTemplate(ShortAnswerSubmittedTextComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSubmittedTextComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSubmittedTextService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ShortAnswerSubmittedText(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.shortAnswerSubmittedTexts[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
