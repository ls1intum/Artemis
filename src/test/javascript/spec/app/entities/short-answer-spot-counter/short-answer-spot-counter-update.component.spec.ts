/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSpotCounterUpdateComponent } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter-update.component';
import { ShortAnswerSpotCounterService } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter.service';
import { ShortAnswerSpotCounter } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter.model';

describe('Component Tests', () => {
    describe('ShortAnswerSpotCounter Management Update Component', () => {
        let comp: ShortAnswerSpotCounterUpdateComponent;
        let fixture: ComponentFixture<ShortAnswerSpotCounterUpdateComponent>;
        let service: ShortAnswerSpotCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSpotCounterUpdateComponent]
            })
                .overrideTemplate(ShortAnswerSpotCounterUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSpotCounterUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSpotCounterService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSpotCounter(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSpotCounter = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSpotCounter();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSpotCounter = entity;
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
