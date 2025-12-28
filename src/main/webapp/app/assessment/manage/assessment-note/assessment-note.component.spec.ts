import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentNoteComponent } from 'app/assessment/manage/assessment-note/assessment-note.component';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';

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
        jest.restoreAllMocks();
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
        it('should create a new AssessmentNote when input is undefined', () => {
            fixture.componentRef.setInput('assessmentNote', undefined);
            fixture.detectChanges();

            const note = component.assessmentNote();
            expect(note).toBeDefined();
            expect(note).toBeInstanceOf(AssessmentNote);
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
        it('should update the note and emit onAssessmentNoteChange', () => {
            const existingNote = new AssessmentNote();
            existingNote.id = 1;

            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const emitSpy = jest.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = {
                target: { value: 'New note text' } as HTMLTextAreaElement,
            } as unknown as Event;

            component.onAssessmentNoteInput(mockEvent);

            expect(existingNote.note).toBe('New note text');
            expect(emitSpy).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalledWith(existingNote);
        });

        it('should emit the same AssessmentNote object reference', () => {
            const existingNote = new AssessmentNote();
            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const emitSpy = jest.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = {
                target: { value: 'Updated text' } as HTMLTextAreaElement,
            } as unknown as Event;

            component.onAssessmentNoteInput(mockEvent);

            const emittedNote = emitSpy.mock.calls[0][0];
            expect(emittedNote).toBe(existingNote);
        });

        it('should handle empty input text', () => {
            const existingNote = new AssessmentNote();
            existingNote.note = 'Original note';

            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const emitSpy = jest.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = {
                target: { value: '' } as HTMLTextAreaElement,
            } as unknown as Event;

            component.onAssessmentNoteInput(mockEvent);

            expect(existingNote.note).toBe('');
            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('should work with a newly created AssessmentNote when input was undefined', () => {
            fixture.componentRef.setInput('assessmentNote', undefined);
            fixture.detectChanges();

            const emitSpy = jest.spyOn(component.onAssessmentNoteChange, 'emit');
            const mockEvent = {
                target: { value: 'Note for new assessment' } as HTMLTextAreaElement,
            } as unknown as Event;

            component.onAssessmentNoteInput(mockEvent);

            expect(emitSpy).toHaveBeenCalledOnce();
            const emittedNote = emitSpy.mock.calls[0][0];
            expect(emittedNote.note).toBe('Note for new assessment');
        });
    });

    describe('template rendering', () => {
        it('should display the note text in the textarea', () => {
            const existingNote = new AssessmentNote();
            existingNote.note = 'Displayed note';

            fixture.componentRef.setInput('assessmentNote', existingNote);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.textContent).toBe('Displayed note');
        });

        it('should display empty text when note is undefined', () => {
            fixture.componentRef.setInput('assessmentNote', undefined);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.textContent).toBe('');
        });

        it('should have the correct textarea id and name', () => {
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.id).toBe('assessment_note');
            expect(textarea.name).toBe('assessment_note');
        });

        it('should have the form-control class on textarea', () => {
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.classList).toContain('form-control');
        });
    });
});
