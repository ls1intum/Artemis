/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionStatisticDetailComponent } from 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-detail.component';
import { DragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';

describe('Component Tests', () => {
    describe('DragAndDropQuestionStatistic Management Detail Component', () => {
        let comp: DragAndDropQuestionStatisticDetailComponent;
        let fixture: ComponentFixture<DragAndDropQuestionStatisticDetailComponent>;
        const route = ({ data: of({ dragAndDropQuestionStatistic: new DragAndDropQuestionStatistic(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropQuestionStatisticDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(DragAndDropQuestionStatisticDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DragAndDropQuestionStatisticDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.dragAndDropQuestionStatistic).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
