/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { DropLocationCounterDetailComponent } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter-detail.component';
import { DropLocationCounterService } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter.service';
import { DropLocationCounter } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter.model';

describe('Component Tests', () => {

    describe('DropLocationCounter Management Detail Component', () => {
        let comp: DropLocationCounterDetailComponent;
        let fixture: ComponentFixture<DropLocationCounterDetailComponent>;
        let service: DropLocationCounterService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DropLocationCounterDetailComponent],
                providers: [
                    DropLocationCounterService
                ]
            })
            .overrideTemplate(DropLocationCounterDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DropLocationCounterDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationCounterService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new DropLocationCounter(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.dropLocationCounter).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
