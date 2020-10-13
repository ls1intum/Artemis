import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';
import { ExerciseResultDetailComponent } from 'app/entities/exercise-result/exercise-result-detail.component';
import { ExerciseResult } from 'app/shared/model/exercise-result.model';

describe('Component Tests', () => {
    describe('ExerciseResult Management Detail Component', () => {
        let comp: ExerciseResultDetailComponent;
        let fixture: ComponentFixture<ExerciseResultDetailComponent>;
        const route = ({ data: of({ exerciseResult: new ExerciseResult(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ExerciseResultDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }],
            })
                .overrideTemplate(ExerciseResultDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ExerciseResultDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should load exerciseResult on init', () => {
                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.exerciseResult).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
