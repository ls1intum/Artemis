/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionDetailComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question-detail.component';
import { DragAndDropQuestionService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question.service';
import { DragAndDropQuestion } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question.model';

describe('Component Tests', () => {

    describe('DragAndDropQuestion Management Detail Component', () => {
        let comp: DragAndDropQuestionDetailComponent;
        let fixture: ComponentFixture<DragAndDropQuestionDetailComponent>;
        let service: DragAndDropQuestionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropQuestionDetailComponent],
                providers: [
                    DragAndDropQuestionService
                ]
            })
            .overrideTemplate(DragAndDropQuestionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropQuestionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new DragAndDropQuestion(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.dragAndDropQuestion).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
