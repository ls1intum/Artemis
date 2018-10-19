/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { AnswerCounterDetailComponent } from 'app/entities/answer-counter/answer-counter-detail.component';
import { AnswerCounter } from 'app/shared/model/answer-counter.model';

describe('Component Tests', () => {
    describe('AnswerCounter Management Detail Component', () => {
        let comp: AnswerCounterDetailComponent;
        let fixture: ComponentFixture<AnswerCounterDetailComponent>;
        const route = ({ data: of({ answerCounter: new AnswerCounter(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [AnswerCounterDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(AnswerCounterDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(AnswerCounterDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.answerCounter).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
