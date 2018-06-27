/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { DropLocationCounterComponent } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter.component';
import { DropLocationCounterService } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter.service';
import { DropLocationCounter } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter.model';

describe('Component Tests', () => {

    describe('DropLocationCounter Management Component', () => {
        let comp: DropLocationCounterComponent;
        let fixture: ComponentFixture<DropLocationCounterComponent>;
        let service: DropLocationCounterService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DropLocationCounterComponent],
                providers: [
                    DropLocationCounterService
                ]
            })
            .overrideTemplate(DropLocationCounterComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DropLocationCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationCounterService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new DropLocationCounter(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.dropLocationCounters[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
