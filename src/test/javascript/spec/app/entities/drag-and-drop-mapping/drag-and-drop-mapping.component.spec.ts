/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropMappingComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping.component';
import { DragAndDropMappingService } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping.service';
import { DragAndDropMapping } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping.model';

describe('Component Tests', () => {

    describe('DragAndDropMapping Management Component', () => {
        let comp: DragAndDropMappingComponent;
        let fixture: ComponentFixture<DragAndDropMappingComponent>;
        let service: DragAndDropMappingService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropMappingComponent],
                providers: [
                    DragAndDropMappingService
                ]
            })
            .overrideTemplate(DragAndDropMappingComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropMappingComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropMappingService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new DragAndDropMapping(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.dragAndDropMappings[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
