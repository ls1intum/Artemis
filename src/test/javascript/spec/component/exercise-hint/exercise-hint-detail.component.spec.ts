/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ExerciseHintDetailComponent } from 'app/exercises/shared/exercise-hint/exercise-hint-detail.component';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { ArtemisTestModule } from '../../test.module';

describe('Component Tests', () => {
    describe('ExerciseHint Management Detail Component', () => {
        let comp: ExerciseHintDetailComponent;
        let fixture: ComponentFixture<ExerciseHintDetailComponent>;
        const exerciseHint = new ExerciseHint();
        exerciseHint.id = 123;
        const route = ({ data: of({ exerciseHint }), params: of({ exerciseId: 1 }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ExerciseHintDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }],
            })
                .overrideTemplate(ExerciseHintDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ExerciseHintDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.exerciseHint).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
