import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ModelingExplanationEditorComponent } from 'app/exercises/modeling/shared/modeling-explanation-editor.component';

describe('ModelingExplanationEditorComponent', () => {
    let fixture: ComponentFixture<ModelingExplanationEditorComponent>;
    let comp: ModelingExplanationEditorComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [ModelingExplanationEditorComponent],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingExplanationEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ModelingExplanationEditorComponent).not.toBeNull();
    });

    it('should change explanation value bidirectionally between component and template', () => {
        comp.explanation = 'Initial Explanation';
        fixture.detectChanges();
        return fixture.whenStable().then(() => {
            const textareaDebugElement = fixture.debugElement.query(By.css('textarea'));
            expect(textareaDebugElement).not.toBeNull();
            const textarea = textareaDebugElement.nativeElement;
            expect(textarea.value).toEqual('Initial Explanation');
            textarea.value = 'Test';
            textarea.dispatchEvent(new Event('input'));
            expect(comp.explanation).toEqual('Test');
            expect(textarea.value).toEqual('Test');

            // Test tab event
            textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));
            textarea.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            expect(textarea.value).toEqual('Test\t');
            expect(comp.explanation).toEqual('Test\t');
        });
    });
});
