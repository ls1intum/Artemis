/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropMappingDetailComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping-detail.component';
import { DragAndDropMappingService } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping.service';
import { DragAndDropMapping } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping.model';

describe('Component Tests', () => {

    describe('DragAndDropMapping Management Detail Component', () => {
        let comp: DragAndDropMappingDetailComponent;
        let fixture: ComponentFixture<DragAndDropMappingDetailComponent>;
        let service: DragAndDropMappingService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropMappingDetailComponent],
                providers: [
                    DragAndDropMappingService
                ]
            })
            .overrideTemplate(DragAndDropMappingDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropMappingDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropMappingService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new DragAndDropMapping(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.dragAndDropMapping).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
