/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropSubmittedAnswerUpdateComponent } from 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer-update.component';
import { DragAndDropSubmittedAnswerService } from 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.service';
import { DragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';

describe('Component Tests', () => {
    describe('DragAndDropSubmittedAnswer Management Update Component', () => {
        let comp: DragAndDropSubmittedAnswerUpdateComponent;
        let fixture: ComponentFixture<DragAndDropSubmittedAnswerUpdateComponent>;
        let service: DragAndDropSubmittedAnswerService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropSubmittedAnswerUpdateComponent]
            })
                .overrideTemplate(DragAndDropSubmittedAnswerUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropSubmittedAnswerUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropSubmittedAnswerService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new DragAndDropSubmittedAnswer(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dragAndDropSubmittedAnswer = entity;
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
                    const entity = new DragAndDropSubmittedAnswer();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dragAndDropSubmittedAnswer = entity;
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
