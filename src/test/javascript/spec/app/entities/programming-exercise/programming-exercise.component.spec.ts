/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { ProgrammingExerciseComponent } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise.component';
import { ProgrammingExerciseService } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise.service';
import { ProgrammingExercise } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise.model';

describe('Component Tests', () => {

    describe('ProgrammingExercise Management Component', () => {
        let comp: ProgrammingExerciseComponent;
        let fixture: ComponentFixture<ProgrammingExerciseComponent>;
        let service: ProgrammingExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ProgrammingExerciseComponent],
                providers: [
                    ProgrammingExerciseService
                ]
            })
            .overrideTemplate(ProgrammingExerciseComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ProgrammingExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new ProgrammingExercise(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.programmingExercises[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
