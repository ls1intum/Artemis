/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArtemisTestModule } from '../../../test.module';
import { ProgrammingExerciseComponent } from 'app/entities/programming-exercise/programming-exercise.component';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';

describe('Component Tests', () => {
    describe('ProgrammingExercise Management Component', () => {
        let comp: ProgrammingExerciseComponent;
        let fixture: ComponentFixture<ProgrammingExerciseComponent>;
        let service: ProgrammingExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ProgrammingExerciseComponent],
                providers: [],
            })
                .overrideTemplate(ProgrammingExerciseComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ProgrammingExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingExerciseService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ProgrammingExercise(123)],
                        headers,
                    }),
                ),
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.programmingExercises[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
