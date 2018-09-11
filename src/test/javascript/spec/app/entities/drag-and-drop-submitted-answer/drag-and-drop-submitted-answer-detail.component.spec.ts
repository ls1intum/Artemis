/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropSubmittedAnswerDetailComponent } from 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer-detail.component';
import { DragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';

describe('Component Tests', () => {
    describe('DragAndDropSubmittedAnswer Management Detail Component', () => {
        let comp: DragAndDropSubmittedAnswerDetailComponent;
        let fixture: ComponentFixture<DragAndDropSubmittedAnswerDetailComponent>;
        const route = ({ data: of({ dragAndDropSubmittedAnswer: new DragAndDropSubmittedAnswer(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropSubmittedAnswerDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(DragAndDropSubmittedAnswerDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DragAndDropSubmittedAnswerDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.dragAndDropSubmittedAnswer).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
