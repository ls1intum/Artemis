/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropSubmittedAnswerComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.component';
import { DragAndDropSubmittedAnswerService } from '../../../../../../main/webapp/app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.service';
import { DragAndDropSubmittedAnswer } from '../../../../../../main/webapp/app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.model';

describe('Component Tests', () => {

    describe('DragAndDropSubmittedAnswer Management Component', () => {
        let comp: DragAndDropSubmittedAnswerComponent;
        let fixture: ComponentFixture<DragAndDropSubmittedAnswerComponent>;
        let service: DragAndDropSubmittedAnswerService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropSubmittedAnswerComponent],
                providers: [
                    DragAndDropSubmittedAnswerService
                ]
            })
            .overrideTemplate(DragAndDropSubmittedAnswerComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropSubmittedAnswerComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropSubmittedAnswerService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new DragAndDropSubmittedAnswer(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.dragAndDropSubmittedAnswers[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
