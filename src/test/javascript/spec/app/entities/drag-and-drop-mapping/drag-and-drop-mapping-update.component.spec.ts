/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropMappingUpdateComponent } from 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping-update.component';
import { DragAndDropMappingService } from 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping.service';
import { DragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';

describe('Component Tests', () => {
    describe('DragAndDropMapping Management Update Component', () => {
        let comp: DragAndDropMappingUpdateComponent;
        let fixture: ComponentFixture<DragAndDropMappingUpdateComponent>;
        let service: DragAndDropMappingService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropMappingUpdateComponent]
            })
                .overrideTemplate(DragAndDropMappingUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropMappingUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropMappingService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new DragAndDropMapping(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.dragAndDropMapping = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new DragAndDropMapping();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.dragAndDropMapping = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));
        });
    });
});
