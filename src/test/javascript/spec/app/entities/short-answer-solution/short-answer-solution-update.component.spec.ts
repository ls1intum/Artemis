/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSolutionUpdateComponent } from 'app/entities/short-answer-solution/short-answer-solution-update.component';
import { ShortAnswerSolutionService } from 'app/entities/short-answer-solution/short-answer-solution.service';
import { ShortAnswerSolution } from 'app/entities/short-answer-solution/short-answer-solution.model';

describe('Component Tests', () => {
    describe('ShortAnswerSolution Management Update Component', () => {
        let comp: ShortAnswerSolutionUpdateComponent;
        let fixture: ComponentFixture<ShortAnswerSolutionUpdateComponent>;
        let service: ShortAnswerSolutionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSolutionUpdateComponent]
            })
                .overrideTemplate(ShortAnswerSolutionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSolutionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSolutionService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSolution(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSolution = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSolution();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSolution = entity;
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
