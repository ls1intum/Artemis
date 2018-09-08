/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionDetailComponent } from 'app/entities/drag-and-drop-question/drag-and-drop-question-detail.component';
import { DragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';

describe('Component Tests', () => {
    describe('DragAndDropQuestion Management Detail Component', () => {
        let comp: DragAndDropQuestionDetailComponent;
        let fixture: ComponentFixture<DragAndDropQuestionDetailComponent>;
        const route = ({ data: of({ dragAndDropQuestion: new DragAndDropQuestion(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropQuestionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(DragAndDropQuestionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DragAndDropQuestionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.dragAndDropQuestion).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
