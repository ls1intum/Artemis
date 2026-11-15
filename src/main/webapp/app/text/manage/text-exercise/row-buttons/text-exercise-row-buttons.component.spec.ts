/**
 * Tests for TextExerciseRowButtonsComponent.
 * This test suite verifies the row button actions for text exercises,
 * including the delete exercise functionality.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextExerciseRowButtonsComponent } from 'app/text/manage/text-exercise/row-buttons/text-exercise-row-buttons.component';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { EntityDeletionStatus, EntityDeletionType } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { MockEntityDeletionService } from 'test/helpers/mocks/service/mock-entity-deletion.service';

describe('TextExercise Row Buttons Component', () => {
    let comp: TextExerciseRowButtonsComponent;
    let fixture: ComponentFixture<TextExerciseRowButtonsComponent>;
    let textExerciseService: TextExerciseService;
    let eventManagerService: EventManager;

    const textExercise: TextExercise = { id: 456, title: 'Text Exercise', type: 'text' } as TextExercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: EntityDeletionService, useClass: MockEntityDeletionService },
                { provide: TranslateService, useClass: MockTranslateService },
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
    it('should keep the list as it is when the background deletion failed', () => {
        (TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService).status = EntityDeletionStatus.FAILED;
        const broadCastSpy = vi.spyOn(eventManagerService, 'broadcast').mockReturnValue();
        vi.spyOn(textExerciseService, 'delete').mockReturnValue(of(new HttpResponse({ body: null })));
        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.detectChanges();

        comp.deleteExercise();

        expect(broadCastSpy).not.toHaveBeenCalled();
    });

    it('should hand the text exercise to the background deletion', () => {
        vi.spyOn(textExerciseService, 'delete').mockReturnValue(of(new HttpResponse({ body: null })));
        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.detectChanges();

        comp.deleteExercise();

        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        expect(deletionService.delete).toHaveBeenCalledWith(expect.objectContaining({ entityType: EntityDeletionType.EXERCISE, entityId: textExercise.id }), expect.anything());
    });
});
