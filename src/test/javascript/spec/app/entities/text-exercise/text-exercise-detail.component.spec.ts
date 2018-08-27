/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { TextExerciseDetailComponent } from '../../../../../../main/webapp/app/entities/text-exercise/text-exercise-detail.component';
import { TextExerciseService } from '../../../../../../main/webapp/app/entities/text-exercise/text-exercise.service';
import { TextExercise } from '../../../../../../main/webapp/app/entities/text-exercise/text-exercise.model';

describe('Component Tests', () => {

    describe('TextExercise Management Detail Component', () => {
        let comp: TextExerciseDetailComponent;
        let fixture: ComponentFixture<TextExerciseDetailComponent>;
        let service: TextExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [TextExerciseDetailComponent],
                providers: [
                    TextExerciseService
                ]
            })
            .overrideTemplate(TextExerciseDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(TextExerciseDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new TextExercise(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.textExercise).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
