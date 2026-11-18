import { inputBinding } from '@angular/core';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseResetDialogComponent } from '../dialog/programming-exercise-reset-dialog.component';
import { ProgrammingExerciseResetButtonDirective } from './programming-exercise-reset-button.directive';

describe('ProgrammingExerciseResetButtonDirective', () => {
    let fixture: DirectiveFixture<ProgrammingExerciseResetButtonDirective>;
    let open: ReturnType<typeof vi.fn>;
    let close: ReturnType<typeof vi.fn>;
    const exercise = new ProgrammingExercise(undefined, undefined);

    beforeEach(() => {
        close = vi.fn();
        open = vi.fn().mockReturnValue({ close });
        TestBed.configureTestingModule({
            providers: [
                { provide: DialogService, useValue: { open } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createDirective(ProgrammingExerciseResetButtonDirective, {
            tagName: 'button',
            bindings: [inputBinding('programmingExercise', () => exercise)],
        });
        fixture.detectChanges();
    });

    it('keeps the instructor reset launcher on the shared destructive palette', () => {
        expect(fixture.nativeElement.classList.contains('tumaet-ui-btn')).toBe(true);
        expect(fixture.nativeElement.classList.contains('tumaet:bg-button-danger')).toBe(true);
        expect(fixture.nativeElement.classList.contains('tumaet:text-button-danger-contrast')).toBe(true);
    });

    it('opens the guarded reset dialog without bubbling and closes it on destruction', () => {
        const event = new MouseEvent('click', { bubbles: true });
        const stop = vi.spyOn(event, 'stopPropagation');
        fixture.nativeElement.dispatchEvent(event);
        expect(stop).toHaveBeenCalledOnce();
        expect(open).toHaveBeenCalledExactlyOnceWith(
            ProgrammingExerciseResetDialogComponent,
            expect.objectContaining({ modal: true, closable: true, closeOnEscape: true, data: { programmingExercise: exercise } }),
        );
        fixture.destroy();
        expect(close).toHaveBeenCalledOnce();
    });
});
