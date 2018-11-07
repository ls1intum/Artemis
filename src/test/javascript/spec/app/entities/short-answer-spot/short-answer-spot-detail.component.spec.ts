/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSpotDetailComponent } from 'app/entities/short-answer-spot/short-answer-spot-detail.component';
import { ShortAnswerSpot } from 'app/entities/short-answer-spot/short-answer-spot.model';

describe('Component Tests', () => {
    describe('ShortAnswerSpot Management Detail Component', () => {
        let comp: ShortAnswerSpotDetailComponent;
        let fixture: ComponentFixture<ShortAnswerSpotDetailComponent>;
        const route = ({ data: of({ shortAnswerSpot: new ShortAnswerSpot(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSpotDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ShortAnswerSpotDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSpotDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.shortAnswerSpot).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
