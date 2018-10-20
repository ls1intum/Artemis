/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { PointCounterComponent } from 'app/entities/point-counter/point-counter.component';
import { PointCounterService } from 'app/entities/point-counter/point-counter.service';
import { PointCounter } from 'app/shared/model/point-counter.model';

describe('Component Tests', () => {
    describe('PointCounter Management Component', () => {
        let comp: PointCounterComponent;
        let fixture: ComponentFixture<PointCounterComponent>;
        let service: PointCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [PointCounterComponent],
                providers: []
            })
                .overrideTemplate(PointCounterComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(PointCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(PointCounterService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new PointCounter(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.pointCounters[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
