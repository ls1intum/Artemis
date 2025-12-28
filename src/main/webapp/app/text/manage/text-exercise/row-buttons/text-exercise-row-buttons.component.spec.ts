/**
 * Tests for TextExerciseRowButtonsComponent.
 * This test suite verifies the row button actions for text exercises,
 * including the delete exercise functionality.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextExerciseRowButtonsComponent } from 'app/text/manage/text-exercise/row-buttons/text-exercise-row-buttons.component';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { EventManager } from 'app/shared/service/event-manager.service';

describe('TextExercise Row Buttons Component', () => {
    setupTestBed({ zoneless: true });
    let comp: TextExerciseRowButtonsComponent;
    let fixture: ComponentFixture<TextExerciseRowButtonsComponent>;
    let textExerciseService: TextExerciseService;
    let eventManagerService: EventManager;

    const textExercise: TextExercise = { id: 456, title: 'Text Exercise', type: 'text' } as TextExercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TextExerciseService, useValue: { delete: vi.fn() } },
                { provide: EventManager, useValue: { broadcast: vi.fn() } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextExerciseRowButtonsComponent);
        comp = fixture.componentInstance;
        textExerciseService = TestBed.inject(TextExerciseService);
        eventManagerService = TestBed.inject(EventManager);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should delete exercise', () => {
        const broadCastSpy = vi.spyOn(eventManagerService, 'broadcast').mockReturnValue();
        vi.spyOn(textExerciseService, 'delete').mockReturnValue(of(new HttpResponse({ body: null })));
        // Use setInput for signal inputs
        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.detectChanges();
        comp.deleteExercise();
        expect(broadCastSpy).toHaveBeenCalledOnce();
    });
});
