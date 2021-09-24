import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ModelingExplanationEditorComponent } from 'app/exercises/modeling/shared/modeling-explanation-editor.component';
import * as chai from 'chai';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';

chai.use(sinonChai);
const expect = chai.expect;

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

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ModelingExplanationEditorComponent).to.be.ok;
    });

    it('should change explanation value bidirectionally between component and template', () => {
        comp.explanation = 'Initial Explanation';
        fixture.detectChanges();
        fixture.whenStable().then(() => {
            const textareaDebugElement = fixture.debugElement.query(By.css('textarea'));
            expect(textareaDebugElement).to.exist;
            const textarea = textareaDebugElement.nativeElement;
            expect(textarea.value).to.equal('Initial Explanation');
            textarea.value = 'Test';
            textarea.dispatchEvent(new Event('input'));
            expect(comp.explanation).to.equal('Test');
            expect(textarea.value).to.equal('Test');

            // Test tab event
            textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));
            textarea.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            expect(textarea.value).to.equal('Test\t');
            expect(comp.explanation).to.equal('Test\t');
        });
    });
});
