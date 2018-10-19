/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionStatisticComponent } from 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { DragAndDropQuestionStatisticService } from 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.service';
import { DragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';

describe('Component Tests', () => {
    describe('DragAndDropQuestionStatistic Management Component', () => {
        let comp: DragAndDropQuestionStatisticComponent;
        let fixture: ComponentFixture<DragAndDropQuestionStatisticComponent>;
        let service: DragAndDropQuestionStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropQuestionStatisticComponent],
                providers: []
            })
                .overrideTemplate(DragAndDropQuestionStatisticComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropQuestionStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionStatisticService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new DragAndDropQuestionStatistic(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.dragAndDropQuestionStatistics[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
