/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { TextExerciseComponent } from '../../../../../../main/webapp/app/entities/text-exercise/text-exercise.component';
import { TextExerciseService } from '../../../../../../main/webapp/app/entities/text-exercise/text-exercise.service';
import { TextExercise } from '../../../../../../main/webapp/app/entities/text-exercise/text-exercise.model';

describe('Component Tests', () => {

    describe('TextExercise Management Component', () => {
        let comp: TextExerciseComponent;
        let fixture: ComponentFixture<TextExerciseComponent>;
        let service: TextExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [TextExerciseComponent],
                providers: [
                    TextExerciseService
                ]
            })
            .overrideTemplate(TextExerciseComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(TextExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new TextExercise(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.textExercises[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
