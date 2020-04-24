import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

describe('ProgrammingExercise Management Detail Component', () => {
    let comp: ProgrammingExerciseDetailComponent;
    let fixture: ComponentFixture<ProgrammingExerciseDetailComponent>;
    const programmingExercise = new ProgrammingExercise();
    programmingExercise.id = 123;
    const route = ({ data: of({ programmingExercise }) } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseDetailComponent],
            providers: [{ provide: ActivatedRoute, useValue: route }],
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
