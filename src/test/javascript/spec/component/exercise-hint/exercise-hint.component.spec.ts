import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

import { ExerciseHintComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.component';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';

describe('ExerciseHint Management Component', () => {
    let comp: ExerciseHintComponent;
    let fixture: ComponentFixture<ExerciseHintComponent>;
    let service: ExerciseHintService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseHintComponent],
            providers: [{ provide: ActivatedRoute, useValue: new MockActivatedRoute({ exerciseId: 15 }) }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHintComponent);
                comp = fixture.componentInstance;
                service = fixture.debugElement.injector.get(ExerciseHintService);
            });
    });

    it('Should call load all on init with exerciseId from route', () => {
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
        expect(service.findByExerciseId).toHaveBeenCalledTimes(1);
        expect(service.findByExerciseId).toHaveBeenCalledWith(15);
        expect(comp.exerciseHints[0]).toEqual(expect.objectContaining({ id: 123 }));
    });

    it('should invoke hint deletion', () => {
        const deleteHintMock = jest.spyOn(service, 'delete');
        const exerciseHint = new ExerciseHint();
        exerciseHint.id = 123;
        comp.exerciseHints = [exerciseHint];
        comp.exerciseId = 15;

        comp.deleteExerciseHint(123);

        expect(deleteHintMock).toHaveBeenCalledTimes(1);
        expect(deleteHintMock).toHaveBeenCalledWith(15, 123);
    });
});
