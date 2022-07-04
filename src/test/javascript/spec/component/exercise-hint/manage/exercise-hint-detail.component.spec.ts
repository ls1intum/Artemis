import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ExerciseHintDetailComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-detail.component';
import { ArtemisTestModule } from '../../../test.module';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';

describe('ExerciseHint Management Detail Component', () => {
    let comp: ExerciseHintDetailComponent;
    let fixture: ComponentFixture<ExerciseHintDetailComponent>;
    const exerciseHint = new ExerciseHint();
    exerciseHint.id = 123;
    const route = { data: of({ exerciseHint }), params: of({ exerciseId: 1 }) } as any as ActivatedRoute;

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
            // WHEN
            comp.ngOnInit();

            // THEN
            expect(comp.exerciseHint).toEqual(expect.objectContaining({ id: 123 }));
        });
    });
});
