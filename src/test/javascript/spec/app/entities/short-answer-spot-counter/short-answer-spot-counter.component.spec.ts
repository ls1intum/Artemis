/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSpotCounterComponent } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter.component';
import { ShortAnswerSpotCounterService } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter.service';
import { ShortAnswerSpotCounter } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter.model';

describe('Component Tests', () => {
    describe('ShortAnswerSpotCounter Management Component', () => {
        let comp: ShortAnswerSpotCounterComponent;
        let fixture: ComponentFixture<ShortAnswerSpotCounterComponent>;
        let service: ShortAnswerSpotCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSpotCounterComponent],
                providers: []
            })
                .overrideTemplate(ShortAnswerSpotCounterComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSpotCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSpotCounterService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ShortAnswerSpotCounter(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.shortAnswerSpotCounters[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
