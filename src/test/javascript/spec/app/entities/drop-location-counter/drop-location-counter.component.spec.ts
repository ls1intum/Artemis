/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { DropLocationCounterComponent } from 'app/entities/drop-location-counter/drop-location-counter.component';
import { DropLocationCounterService } from 'app/entities/drop-location-counter/drop-location-counter.service';
import { DropLocationCounter } from 'app/shared/model/drop-location-counter.model';

describe('Component Tests', () => {
    describe('DropLocationCounter Management Component', () => {
        let comp: DropLocationCounterComponent;
        let fixture: ComponentFixture<DropLocationCounterComponent>;
        let service: DropLocationCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DropLocationCounterComponent],
                providers: []
            })
                .overrideTemplate(DropLocationCounterComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DropLocationCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationCounterService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new DropLocationCounter(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.dropLocationCounters[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
