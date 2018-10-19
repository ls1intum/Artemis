/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropMappingComponent } from 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping.component';
import { DragAndDropMappingService } from 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping.service';
import { DragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';

describe('Component Tests', () => {
    describe('DragAndDropMapping Management Component', () => {
        let comp: DragAndDropMappingComponent;
        let fixture: ComponentFixture<DragAndDropMappingComponent>;
        let service: DragAndDropMappingService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropMappingComponent],
                providers: []
            })
                .overrideTemplate(DragAndDropMappingComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropMappingComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropMappingService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new DragAndDropMapping(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.dragAndDropMappings[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
