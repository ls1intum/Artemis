/**
 * Vitest tests for ResizableInstructionsComponent.
 * Tests the resizable instructions panel functionality.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Component, Directive, Input, Pipe, PipeTransform } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ResizableInstructionsComponent } from 'app/text/manage/example-text-submission/resizable-instructions/resizable-instructions.component';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';

@Component({
    selector: 'fa-icon',
    template: '',
    standalone: true,
})
class FaIconStubComponent {
    @Input() icon: any;
}

@Directive({
    selector: '[jhiTranslate]',
    standalone: true,
})
class TranslateDirectiveStub {
    @Input('jhiTranslate') key: string;
}

@Component({
    selector: 'jhi-structured-grading-instructions-assessment-layout',
    template: '',
    standalone: true,
})
class StructuredLayoutStubComponent {
    @Input() readonly: boolean;
    @Input() criteria: GradingCriterion[];
}

@Pipe({
    name: 'htmlForMarkdown',
    standalone: true,
})
class HtmlForMarkdownPipeStub implements PipeTransform {
    transform = vi.fn((value: string) => `converted:${value}`);
}

describe('ResizableInstructionsComponent', () => {
    setupTestBed({ zoneless: true });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ResizableInstructionsComponent, FaIconStubComponent, TranslateDirectiveStub, StructuredLayoutStubComponent, HtmlForMarkdownPipeStub],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TranslateStore, useValue: {} },
            ],
        })
            .overrideComponent(ResizableInstructionsComponent, {
                set: { imports: [FaIconStubComponent, TranslateDirectiveStub, StructuredLayoutStubComponent, HtmlForMarkdownPipeStub] },
            })
            .compileComponents();
    });

    it('should render provided instruction sections', () => {
        const fixture = TestBed.createComponent(ResizableInstructionsComponent);
        // Use setInput for signal inputs
        fixture.componentRef.setInput('problemStatement', 'problem');
        fixture.componentRef.setInput('sampleSolution', 'solution');
        fixture.componentRef.setInput('gradingInstructions', 'grading');
        fixture.componentRef.setInput('criteria', []);
        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('toggleCollapse', () => {});
        fixture.detectChanges();

        const markdownBlocks = fixture.nativeElement.querySelectorAll('.markdown-preview');
        expect(markdownBlocks).toHaveLength(3);
        expect(markdownBlocks[0].innerHTML).toContain('converted:problem');
        expect(markdownBlocks[1].innerHTML).toContain('converted:solution');
        expect(markdownBlocks[2].innerHTML).toContain('converted:grading');
    });

    it('should toggle collapse with provided id', () => {
        const fixture = TestBed.createComponent(ResizableInstructionsComponent);
        const toggleSpy = vi.fn();
        // Use setInput for signal inputs
        fixture.componentRef.setInput('toggleCollapse', toggleSpy);
        fixture.componentRef.setInput('toggleCollapseId', 'instructions');
        fixture.componentRef.setInput('criteria', []);
        fixture.componentRef.setInput('readOnly', false);
        fixture.detectChanges();

        fixture.nativeElement.querySelector('.card-header').dispatchEvent(new Event('click'));

        expect(toggleSpy).toHaveBeenCalledWith(expect.any(Event), 'instructions');
    });

    it('should forward criteria and readonly flag to structured layout', () => {
        const fixture = TestBed.createComponent(ResizableInstructionsComponent);
        const criteria = [{ id: 5 } as GradingCriterion];
        // Use setInput for signal inputs
        fixture.componentRef.setInput('criteria', criteria);
        fixture.componentRef.setInput('readOnly', true);
        fixture.componentRef.setInput('toggleCollapse', () => {});

        fixture.detectChanges();

        const layout = fixture.debugElement.query(By.directive(StructuredLayoutStubComponent)).componentInstance as StructuredLayoutStubComponent;
        expect(layout.criteria).toEqual(criteria);
        expect(layout.readonly).toBe(true);
    });
});
