/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSubmittedAnswerDetailComponent } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer-detail.component';
import { ShortAnswerSubmittedAnswer } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer.model';

describe('Component Tests', () => {
    describe('ShortAnswerSubmittedAnswer Management Detail Component', () => {
        let comp: ShortAnswerSubmittedAnswerDetailComponent;
        let fixture: ComponentFixture<ShortAnswerSubmittedAnswerDetailComponent>;
        const route = ({ data: of({ shortAnswerSubmittedAnswer: new ShortAnswerSubmittedAnswer(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSubmittedAnswerDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ShortAnswerSubmittedAnswerDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSubmittedAnswerDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.shortAnswerSubmittedAnswer).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
