/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question.component';
import { DragAndDropQuestionService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question.service';
import { DragAndDropQuestion } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question.model';

describe('Component Tests', () => {

    describe('DragAndDropQuestion Management Component', () => {
        let comp: DragAndDropQuestionComponent;
        let fixture: ComponentFixture<DragAndDropQuestionComponent>;
        let service: DragAndDropQuestionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropQuestionComponent],
                providers: [
                    DragAndDropQuestionService
                ]
            })
            .overrideTemplate(DragAndDropQuestionComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropQuestionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new DragAndDropQuestion(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.dragAndDropQuestions[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
