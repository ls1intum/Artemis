/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionStatisticComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { DragAndDropQuestionStatisticService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.service';
import { DragAndDropQuestionStatistic } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.model';

describe('Component Tests', () => {

    describe('DragAndDropQuestionStatistic Management Component', () => {
        let comp: DragAndDropQuestionStatisticComponent;
        let fixture: ComponentFixture<DragAndDropQuestionStatisticComponent>;
        let service: DragAndDropQuestionStatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropQuestionStatisticComponent],
                providers: [
                    DragAndDropQuestionStatisticService
                ]
            })
            .overrideTemplate(DragAndDropQuestionStatisticComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropQuestionStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionStatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new DragAndDropQuestionStatistic(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.dragAndDropQuestionStatistics[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
