/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSubmittedTextUpdateComponent } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text-update.component';
import { ShortAnswerSubmittedTextService } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.service';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.model';

describe('Component Tests', () => {
    describe('ShortAnswerSubmittedText Management Update Component', () => {
        let comp: ShortAnswerSubmittedTextUpdateComponent;
        let fixture: ComponentFixture<ShortAnswerSubmittedTextUpdateComponent>;
        let service: ShortAnswerSubmittedTextService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSubmittedTextUpdateComponent]
            })
                .overrideTemplate(ShortAnswerSubmittedTextUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSubmittedTextUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSubmittedTextService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSubmittedText(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSubmittedText = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSubmittedText();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSubmittedText = entity;
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
