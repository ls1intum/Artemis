/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionUpdateComponent } from 'app/entities/drag-and-drop-question/drag-and-drop-question-update.component';
import { DragAndDropQuestionService } from 'app/entities/drag-and-drop-question/drag-and-drop-question.service';
import { DragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';

describe('Component Tests', () => {
    describe('DragAndDropQuestion Management Update Component', () => {
        let comp: DragAndDropQuestionUpdateComponent;
        let fixture: ComponentFixture<DragAndDropQuestionUpdateComponent>;
        let service: DragAndDropQuestionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropQuestionUpdateComponent]
            })
                .overrideTemplate(DragAndDropQuestionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropQuestionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new DragAndDropQuestion(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dragAndDropQuestion = entity;
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
                    const entity = new DragAndDropQuestion();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dragAndDropQuestion = entity;
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
