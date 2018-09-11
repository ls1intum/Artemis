/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropSubmittedAnswerComponent } from 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.component';
import { DragAndDropSubmittedAnswerService } from 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.service';
import { DragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';

describe('Component Tests', () => {
    describe('DragAndDropSubmittedAnswer Management Component', () => {
        let comp: DragAndDropSubmittedAnswerComponent;
        let fixture: ComponentFixture<DragAndDropSubmittedAnswerComponent>;
        let service: DragAndDropSubmittedAnswerService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropSubmittedAnswerComponent],
                providers: []
            })
                .overrideTemplate(DragAndDropSubmittedAnswerComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropSubmittedAnswerComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropSubmittedAnswerService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new DragAndDropSubmittedAnswer(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.dragAndDropSubmittedAnswers[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
