import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextExerciseRowButtonsComponent } from 'app/text/manage/text-exercise/row-buttons/text-exercise-row-buttons.component';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { EventManager } from 'app/shared/service/event-manager.service';

describe('TextExercise Row Buttons Component', () => {
    let comp: TextExerciseRowButtonsComponent;
    let fixture: ComponentFixture<TextExerciseRowButtonsComponent>;
    let textExerciseService: TextExerciseService;
    let eventManagerService: EventManager;

    const textExercise: TextExercise = { id: 456, title: 'Text Exercise', type: 'text' } as TextExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TextExerciseService, useValue: { delete: jest.fn() } },
                { provide: EventManager, useValue: { broadcast: jest.fn() } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextExerciseRowButtonsComponent);
        comp = fixture.componentInstance;
        textExerciseService = TestBed.inject(TextExerciseService);
        eventManagerService = TestBed.inject(EventManager);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should delete exercise', () => {
        const broadCastSpy = jest.spyOn(eventManagerService, 'broadcast').mockReturnValue();
        jest.spyOn(textExerciseService, 'delete').mockReturnValue(of(new HttpResponse({ body: null })));
        comp.exercise = textExercise;
        comp.deleteExercise();
        expect(broadCastSpy).toHaveBeenCalledOnce();
    });
});
