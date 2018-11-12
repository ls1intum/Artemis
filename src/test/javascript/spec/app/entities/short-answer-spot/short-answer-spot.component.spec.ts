/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSpotComponent } from 'app/entities/short-answer-spot/short-answer-spot.component';
import { ShortAnswerSpotService } from 'app/entities/short-answer-spot/short-answer-spot.service';
import { ShortAnswerSpot } from 'app/entities/short-answer-spot/short-answer-spot.model';

describe('Component Tests', () => {
    describe('ShortAnswerSpot Management Component', () => {
        let comp: ShortAnswerSpotComponent;
        let fixture: ComponentFixture<ShortAnswerSpotComponent>;
        let service: ShortAnswerSpotService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSpotComponent],
                providers: []
            })
                .overrideTemplate(ShortAnswerSpotComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSpotComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSpotService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ShortAnswerSpot(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.shortAnswerSpots[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
