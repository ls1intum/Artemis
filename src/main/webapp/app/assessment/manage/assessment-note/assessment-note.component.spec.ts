import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentNoteComponent } from 'app/assessment/manage/assessment-note/assessment-note.component';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { TumAetUiInputDirective } from '@tumaet/ui-angular';

describe('AssessmentNoteComponent', () => {
    let component: AssessmentNoteComponent;
    let fixture: ComponentFixture<AssessmentNoteComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(AssessmentNoteComponent, {
                remove: { imports: [TranslateDirective] },
                add: { imports: [MockDirective(TranslateDirective)] },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentNoteComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('component creation', () => {
        it('should create the component', () => {
            fixture.detectChanges();
            expect(component).toBeTruthy();
        });

        it('should have onAssessmentNoteChange output defined', () => {
            expect(component.onAssessmentNoteChange).toBeDefined();
        });
    });

    describe('assessmentNote input', () => {
        it('should preserve undefined when input is undefined', () => {
            fixture.componentRef.setInput('assessmentNote', undefined);
            fixture.detectChanges();

            const note = component.assessmentNote();
            expect(note).toBeUndefined();
        });

        it('should use the provided AssessmentNote when input is defined', () => {
            const existingNote = new AssessmentNote();
            existingNote.id = 1;
            existingNote.note = 'Test note';

            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const note = component.assessmentNote();
            expect(note).toBe(existingNote);
            expect(note?.id).toBe(1);
            expect(note?.note).toBe('Test note');
        });

        it('should handle AssessmentNote with empty note', () => {
            const emptyNote = new AssessmentNote();
            emptyNote.note = '';

            fixture.componentRef.setInput('assessmentNote', emptyNote);
            fixture.detectChanges();

            expect(component.assessmentNote()?.note).toBe('');
        });
    });

    describe('onAssessmentNoteInput', () => {
        const createMockInputEvent = (value: string): Event => {
            const textarea = document.createElement('textarea');
            textarea.value = value;
            return { target: textarea } as unknown as Event;
        };

        it('should emit a new note object with updated text without mutating the original', () => {
            const existingNote = new AssessmentNote();
            existingNote.id = 1;
            existingNote.note = 'Original text';

            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const emitSpy = vi.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = createMockInputEvent('New note text');

            component.onAssessmentNoteInput(mockEvent);

            expect(existingNote.note).toBe('Original text'); // Original not mutated
            expect(emitSpy).toHaveBeenCalledTimes(1);
            const emittedNote = emitSpy.mock.calls[0][0];
            expect(emittedNote.note).toBe('New note text');
            expect(emittedNote.id).toBe(1); // Preserves other properties
            expect(emittedNote).not.toBe(existingNote); // New object
        });

        it('should emit a new AssessmentNote object, not the same reference', () => {
            const existingNote = new AssessmentNote();
            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const emitSpy = vi.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = createMockInputEvent('Updated text');

            component.onAssessmentNoteInput(mockEvent);

            const emittedNote = emitSpy.mock.calls[0][0];
            expect(emittedNote).not.toBe(existingNote);
            expect(emittedNote.note).toBe('Updated text');
        });

        it('should handle empty input text', () => {
            const existingNote = new AssessmentNote();
            existingNote.note = 'Original note';

            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const emitSpy = vi.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = createMockInputEvent('');

            component.onAssessmentNoteInput(mockEvent);

            expect(existingNote.note).toBe('Original note'); // Original not mutated
            expect(emitSpy).toHaveBeenCalledTimes(1);
            const emittedNote = emitSpy.mock.calls[0][0];
            expect(emittedNote.note).toBe('');
        });

        it('should create a new AssessmentNote when input was undefined', () => {
            fixture.componentRef.setInput('assessmentNote', undefined);
            fixture.detectChanges();

            const emitSpy = vi.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = createMockInputEvent('Note for new assessment');

            component.onAssessmentNoteInput(mockEvent);

            expect(emitSpy).toHaveBeenCalledTimes(1);
            const emittedNote = emitSpy.mock.calls[0][0];
            expect(emittedNote.note).toBe('Note for new assessment');
        });

        it('should not emit when target is not an HTMLTextAreaElement', () => {
            fixture.componentRef.setInput('assessmentNote', new AssessmentNote());
            fixture.detectChanges();

            const emitSpy = vi.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = { target: document.createElement('input') } as unknown as Event;

            component.onAssessmentNoteInput(mockEvent);

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('auto-growing textarea', () => {
        // jsdom does not lay out text, so the content height the browser would report is mocked.
        const mockScrollHeight = (height: number) => vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(height);
        const getTextarea = (): HTMLTextAreaElement => fixture.nativeElement.querySelector('textarea');
        const noteWithText = (text: string) => {
            const note = new AssessmentNote();
            note.note = text;
            return note;
        };

        it('should size the textarea to a long note that is present on initial load', () => {
            mockScrollHeight(240);

            fixture.componentRef.setInput('assessmentNote', noteWithText('long note\n'.repeat(20)));
            fixture.detectChanges();

            expect(getTextarea().style.height).toBe('240px');
        });

        it('should size the textarea once a note arrives asynchronously', () => {
            mockScrollHeight(60);
            fixture.componentRef.setInput('assessmentNote', undefined);
            fixture.detectChanges();
            expect(getTextarea().style.height).toBe('60px');

            mockScrollHeight(320);
            fixture.componentRef.setInput('assessmentNote', noteWithText('late arriving long note\n'.repeat(25)));
            fixture.detectChanges();

            expect(getTextarea().style.height).toBe('320px');
        });

        it('should grow and shrink the textarea while the tutor types', () => {
            mockScrollHeight(80);
            fixture.componentRef.setInput('assessmentNote', noteWithText('short'));
            fixture.detectChanges();
            const textarea = getTextarea();
            expect(textarea.style.height).toBe('80px');

            mockScrollHeight(400);
            textarea.value = 'a much longer note\n'.repeat(30);
            textarea.dispatchEvent(new Event('input'));
            expect(textarea.style.height).toBe('400px');

            mockScrollHeight(80);
            textarea.value = 'short again';
            textarea.dispatchEvent(new Event('input'));
            expect(textarea.style.height).toBe('80px');
        });

        it('should include the border in the height so a border-box textarea does not scroll', () => {
            mockScrollHeight(100);
            vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(104);
            vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(102);

            fixture.componentRef.setInput('assessmentNote', noteWithText('note'));
            fixture.detectChanges();

            expect(getTextarea().style.height).toBe('102px');
        });
    });

    describe('template rendering', () => {
        it('should display the note text in the textarea', () => {
            const existingNote = new AssessmentNote();
            existingNote.note = 'Displayed note';

            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.value).toBe('Displayed note');
        });

        it('should display empty text when note is undefined', () => {
            fixture.componentRef.setInput('assessmentNote', undefined);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.value).toBe('');
        });

        it('should have the correct textarea id and name', () => {
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.id).toBe('assessment_note');
            expect(textarea.name).toBe('assessment_note');
        });

        it('should apply the TUM AET UI textarea directive and the component styling hook', () => {
            fixture.detectChanges();

            const textarea = fixture.debugElement.query(By.directive(TumAetUiInputDirective));
            expect(textarea.nativeElement).toBeInstanceOf(HTMLTextAreaElement);
            expect(textarea.nativeElement.classList).toContain('assessment-note__textarea');
        });
    });
});
