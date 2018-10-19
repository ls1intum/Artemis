/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionStatisticUpdateComponent } from 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-update.component';
import { DragAndDropQuestionStatisticService } from 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.service';
import { DragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';

describe('Component Tests', () => {
    describe('DragAndDropQuestionStatistic Management Update Component', () => {
        let comp: DragAndDropQuestionStatisticUpdateComponent;
        let fixture: ComponentFixture<DragAndDropQuestionStatisticUpdateComponent>;
        let service: DragAndDropQuestionStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropQuestionStatisticUpdateComponent]
            })
                .overrideTemplate(DragAndDropQuestionStatisticUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropQuestionStatisticUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionStatisticService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new DragAndDropQuestionStatistic(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dragAndDropQuestionStatistic = entity;
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
                    const entity = new DragAndDropQuestionStatistic();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dragAndDropQuestionStatistic = entity;
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
