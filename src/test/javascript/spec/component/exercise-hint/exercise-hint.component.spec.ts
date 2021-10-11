import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ExerciseHintComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.component';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { ArtemisTestModule } from '../../test.module';

describe('ExerciseHint Management Component', () => {
    let comp: ExerciseHintComponent;
    let fixture: ComponentFixture<ExerciseHintComponent>;
    let service: ExerciseHintService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseHintComponent],
            providers: [],
        })
            .overrideTemplate(ExerciseHintComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseHintComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(ExerciseHintService);
    });

    it('Should call load all on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        const hint = new ExerciseHint();
        hint.id = 123;

        jest.spyOn(service, 'findByExerciseId').mockReturnValue(
            of(
                new HttpResponse({
                    body: [hint],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.findByExerciseId).toHaveBeenCalled();
        expect(comp.exerciseHints[0]).toEqual(expect.objectContaining({ id: 123 }));
    });
});
