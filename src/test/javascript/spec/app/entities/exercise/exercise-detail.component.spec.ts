/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ExerciseDetailComponent } from 'app/entities/exercise/exercise-detail.component';
import { Exercise } from 'app/shared/model/exercise.model';

describe('Component Tests', () => {
    describe('Exercise Management Detail Component', () => {
        let comp: ExerciseDetailComponent;
        let fixture: ComponentFixture<ExerciseDetailComponent>;
        const route = ({ data: of({ exercise: new Exercise(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ExerciseDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ExerciseDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ExerciseDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.exercise).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
