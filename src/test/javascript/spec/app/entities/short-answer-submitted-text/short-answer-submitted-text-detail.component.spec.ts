/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSubmittedTextDetailComponent } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text-detail.component';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.model';

describe('Component Tests', () => {
    describe('ShortAnswerSubmittedText Management Detail Component', () => {
        let comp: ShortAnswerSubmittedTextDetailComponent;
        let fixture: ComponentFixture<ShortAnswerSubmittedTextDetailComponent>;
        const route = ({ data: of({ shortAnswerSubmittedText: new ShortAnswerSubmittedText(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSubmittedTextDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ShortAnswerSubmittedTextDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSubmittedTextDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.shortAnswerSubmittedText).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
