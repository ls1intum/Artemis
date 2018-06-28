/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { DropLocationComponent } from '../../../../../../main/webapp/app/entities/drop-location/drop-location.component';
import { DropLocationService } from '../../../../../../main/webapp/app/entities/drop-location/drop-location.service';
import { DropLocation } from '../../../../../../main/webapp/app/entities/drop-location/drop-location.model';

describe('Component Tests', () => {

    describe('DropLocation Management Component', () => {
        let comp: DropLocationComponent;
        let fixture: ComponentFixture<DropLocationComponent>;
        let service: DropLocationService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DropLocationComponent],
                providers: [
                    DropLocationService
                ]
            })
            .overrideTemplate(DropLocationComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DropLocationComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new DropLocation(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.dropLocations[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
