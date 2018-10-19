/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DropLocationCounterUpdateComponent } from 'app/entities/drop-location-counter/drop-location-counter-update.component';
import { DropLocationCounterService } from 'app/entities/drop-location-counter/drop-location-counter.service';
import { DropLocationCounter } from 'app/shared/model/drop-location-counter.model';

describe('Component Tests', () => {
    describe('DropLocationCounter Management Update Component', () => {
        let comp: DropLocationCounterUpdateComponent;
        let fixture: ComponentFixture<DropLocationCounterUpdateComponent>;
        let service: DropLocationCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DropLocationCounterUpdateComponent]
            })
                .overrideTemplate(DropLocationCounterUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DropLocationCounterUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationCounterService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new DropLocationCounter(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dropLocationCounter = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );

            it(
                'Should call create service on save for new entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new DropLocationCounter();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dropLocationCounter = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.create).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );
        });
    });
});
