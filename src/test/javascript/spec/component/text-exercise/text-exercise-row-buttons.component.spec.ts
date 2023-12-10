import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ArtemisTestModule } from '../../test.module';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextExerciseRowButtonsComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-row-buttons.component';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { EventManager } from 'app/core/util/event-manager.service';

describe('TextExercise Row Buttons Component', () => {
    let comp: TextExerciseRowButtonsComponent;
    let fixture: ComponentFixture<TextExerciseRowButtonsComponent>;
    let textExerciseService: TextExerciseService;
    let eventManagerService: EventManager;

    const textExercise: TextExercise = { id: 456, title: 'Text Exercise', type: 'text' } as TextExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextExerciseRowButtonsComponent],
            providers: [
                { provide: TextExerciseService, useValue: { delete: jest.fn() } },
                { provide: EventManager, useValue: { broadcast: jest.fn() } },
            ],
        })
            .overrideTemplate(TextExerciseRowButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TextExerciseRowButtonsComponent);
        comp = fixture.componentInstance;
        textExerciseService = fixture.debugElement.injector.get(TextExerciseService);
        eventManagerService = fixture.debugElement.injector.get(EventManager);
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
