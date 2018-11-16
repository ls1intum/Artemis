/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSpotCounterDetailComponent } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter-detail.component';
import { ShortAnswerSpotCounter } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter.model';

describe('Component Tests', () => {
    describe('ShortAnswerSpotCounter Management Detail Component', () => {
        let comp: ShortAnswerSpotCounterDetailComponent;
        let fixture: ComponentFixture<ShortAnswerSpotCounterDetailComponent>;
        const route = ({ data: of({ shortAnswerSpotCounter: new ShortAnswerSpotCounter(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSpotCounterDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ShortAnswerSpotCounterDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSpotCounterDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.shortAnswerSpotCounter).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
