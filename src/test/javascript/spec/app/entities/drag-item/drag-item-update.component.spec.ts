/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragItemUpdateComponent } from 'app/entities/drag-item/drag-item-update.component';
import { DragItemService } from 'app/entities/drag-item/drag-item.service';
import { DragItem } from 'app/shared/model/drag-item.model';

describe('Component Tests', () => {
    describe('DragItem Management Update Component', () => {
        let comp: DragItemUpdateComponent;
        let fixture: ComponentFixture<DragItemUpdateComponent>;
        let service: DragItemService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragItemUpdateComponent]
            })
                .overrideTemplate(DragItemUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragItemUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragItemService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new DragItem(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.dragItem = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new DragItem();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.dragItem = entity;
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
