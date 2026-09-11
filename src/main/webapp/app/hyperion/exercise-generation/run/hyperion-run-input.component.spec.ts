import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionRunInputComponent } from './hyperion-run-input.component';

describe('HyperionRunInputComponent', () => {
    let fixture: ComponentFixture<HyperionRunInputComponent>;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HyperionRunInputComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(HyperionRunInputComponent);
    });

    it('shows the original brief without interpreting markup as HTML', () => {
        fixture.componentRef.setInput('context', { prompt: '<img src=x onerror=alert(1)>\nImplement a stack.' });
        fixture.detectChanges();
        const prompt = fixture.nativeElement.querySelector('[data-testid="hyperion-run-input-prompt"]');
        expect(prompt.textContent).toContain('<img src=x onerror=alert(1)>');
        expect(prompt.querySelector('img')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.hyperion.generation.run.generationInput');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-input-feedback"]')).toBeNull();
    });

    it('shows adaptation instructions and the selected feedback together', () => {
        fixture.componentRef.setInput('adapting', true);
        fixture.componentRef.setInput('context', {
            prompt: 'Preserve the public API.',
            reviewFeedback: [{ targetType: 'SOLUTION_REPO', filePath: 'src/Stack.java', lineNumber: 12, comments: ['Handle empty stacks.', 'Include an example.'] }],
        });
        fixture.detectChanges();
        const content = fixture.nativeElement.textContent;
        expect(content).toContain('artemisApp.hyperion.generation.run.adaptationInput');
        for (const text of ['Preserve the public API.', 'src/Stack.java', '12', 'Handle empty stacks.', 'Include an example.']) {
            expect(content).toContain(text);
        }
    });

    it('renders selected feedback without requiring additional instructions', () => {
        fixture.componentRef.setInput('adapting', true);
        fixture.componentRef.setInput('context', { reviewFeedback: [{ targetType: 'PROBLEM_STATEMENT', comments: ['Clarify the edge cases.'] }] });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-input-prompt"]')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('Clarify the edge cases.');
    });
});
