/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ProgrammingExerciseDetailComponent } from 'app/entities/programming-exercise/programming-exercise-detail.component';
import { ProgrammingExercise } from 'app/shared/model/programming-exercise.model';

describe('Component Tests', () => {
    describe('ProgrammingExercise Management Detail Component', () => {
        let comp: ProgrammingExerciseDetailComponent;
        let fixture: ComponentFixture<ProgrammingExerciseDetailComponent>;
        const route = ({ data: of({ programmingExercise: new ProgrammingExercise(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ProgrammingExerciseDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ProgrammingExerciseDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ProgrammingExerciseDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.programmingExercise).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
