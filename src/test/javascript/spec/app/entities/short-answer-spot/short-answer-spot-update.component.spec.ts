/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSpotUpdateComponent } from 'app/entities/short-answer-spot/short-answer-spot-update.component';
import { ShortAnswerSpotService } from 'app/entities/short-answer-spot/short-answer-spot.service';
import { ShortAnswerSpot } from 'app/entities/short-answer-spot/short-answer-spot.model';

describe('Component Tests', () => {
    describe('ShortAnswerSpot Management Update Component', () => {
        let comp: ShortAnswerSpotUpdateComponent;
        let fixture: ComponentFixture<ShortAnswerSpotUpdateComponent>;
        let service: ShortAnswerSpotService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSpotUpdateComponent]
            })
                .overrideTemplate(ShortAnswerSpotUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSpotUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSpotService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSpot(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSpot = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSpot();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSpot = entity;
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
