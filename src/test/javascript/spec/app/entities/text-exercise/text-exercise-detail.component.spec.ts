import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';
import { TextExerciseDetailComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-detail.component';
import { TextExercise } from 'app/entities/text-exercise.model';

describe('Component Tests', () => {
    describe('TextExercise Management Detail Component', () => {
        let comp: TextExerciseDetailComponent;
        let fixture: ComponentFixture<TextExerciseDetailComponent>;
        const route = ({ data: of({ textExercise: new TextExercise(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [TextExerciseDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }],
            })
                .overrideTemplate(TextExerciseDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(TextExerciseDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.textExercise).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
