/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { PointCounterUpdateComponent } from 'app/entities/point-counter/point-counter-update.component';
import { PointCounterService } from 'app/entities/point-counter/point-counter.service';
import { PointCounter } from 'app/shared/model/point-counter.model';

describe('Component Tests', () => {
    describe('PointCounter Management Update Component', () => {
        let comp: PointCounterUpdateComponent;
        let fixture: ComponentFixture<PointCounterUpdateComponent>;
        let service: PointCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [PointCounterUpdateComponent]
            })
                .overrideTemplate(PointCounterUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(PointCounterUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(PointCounterService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new PointCounter(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.pointCounter = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new PointCounter();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.pointCounter = entity;
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
