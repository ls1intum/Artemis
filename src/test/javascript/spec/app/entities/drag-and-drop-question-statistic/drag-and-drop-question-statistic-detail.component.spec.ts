/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionStatisticDetailComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-detail.component';
import { DragAndDropQuestionStatisticService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.service';
import { DragAndDropQuestionStatistic } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.model';

describe('Component Tests', () => {

    describe('DragAndDropQuestionStatistic Management Detail Component', () => {
        let comp: DragAndDropQuestionStatisticDetailComponent;
        let fixture: ComponentFixture<DragAndDropQuestionStatisticDetailComponent>;
        let service: DragAndDropQuestionStatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropQuestionStatisticDetailComponent],
                providers: [
                    DragAndDropQuestionStatisticService
                ]
            })
            .overrideTemplate(DragAndDropQuestionStatisticDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropQuestionStatisticDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionStatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new DragAndDropQuestionStatistic(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.dragAndDropQuestionStatistic).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
