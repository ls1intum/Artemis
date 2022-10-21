import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { EventManager } from 'app/core/util/event-manager.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import { ExerciseHintComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.component';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { of } from 'rxjs';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ArtemisTestModule } from '../../../test.module';

describe('ExerciseHint Management Component', () => {
    let comp: ExerciseHintComponent;
    let fixture: ComponentFixture<ExerciseHintComponent>;
    let service: ExerciseHintService;
    let eventManager: EventManager;

    const programmingExercise = new ProgrammingExercise(undefined, undefined);
    programmingExercise.id = 15;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseHintComponent],
            providers: [{ provide: ActivatedRoute, useValue: new MockActivatedRoute({ exerciseId: 15, exercise: programmingExercise }) }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHintComponent);
                comp = fixture.componentInstance;
                service = TestBed.inject(ExerciseHintService);
                eventManager = TestBed.inject(EventManager);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call load all on init with exerciseId from route', () => {
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
        expect(service.findByExerciseId).toHaveBeenCalledOnce();
        expect(service.findByExerciseId).toHaveBeenCalledWith(15);
        expect(comp.exerciseHints[0]).toEqual(expect.objectContaining({ id: 123 }));
    });

    it('should invoke hint deletion', () => {
        const deleteHintMock = jest.spyOn(service, 'delete').mockReturnValue(of({} as HttpResponse<void>));
        const broadcastSpy = jest.spyOn(eventManager, 'broadcast');
        const exerciseHint = new ExerciseHint();
        exerciseHint.id = 123;
        comp.exerciseHints = [exerciseHint];
        comp.exercise = programmingExercise;

        comp.deleteExerciseHint(123);

        expect(deleteHintMock).toHaveBeenCalledOnce();
        expect(deleteHintMock).toHaveBeenCalledWith(15, 123);
        expect(broadcastSpy).toHaveBeenCalledOnce();
        expect(broadcastSpy).toHaveBeenCalledWith({
            name: 'exerciseHintListModification',
            content: 'Deleted an exerciseHint',
        });
    });

    it('should track item id', () => {
        const exerciseHint = new ExerciseHint();
        exerciseHint.id = 1;
        expect(comp.trackId(0, exerciseHint)).toBe(1);
    });
});
