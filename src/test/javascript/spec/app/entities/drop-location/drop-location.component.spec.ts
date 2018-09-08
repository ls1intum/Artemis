/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { DropLocationComponent } from 'app/entities/drop-location/drop-location.component';
import { DropLocationService } from 'app/entities/drop-location/drop-location.service';
import { DropLocation } from 'app/shared/model/drop-location.model';

describe('Component Tests', () => {
    describe('DropLocation Management Component', () => {
        let comp: DropLocationComponent;
        let fixture: ComponentFixture<DropLocationComponent>;
        let service: DropLocationService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DropLocationComponent],
                providers: []
            })
                .overrideTemplate(DropLocationComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DropLocationComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new DropLocation(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.dropLocations[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
