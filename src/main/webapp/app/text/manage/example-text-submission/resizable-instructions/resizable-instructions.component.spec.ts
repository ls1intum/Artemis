import { Component, Directive, Input, Pipe, PipeTransform } from '@angular/core';
import { TestBed } from '@angular/core/testing';
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
    transform = jest.fn((value: string) => `converted:${value}`);
}

describe('ResizableInstructionsComponent', () => {
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
        fixture.componentInstance.problemStatement = 'problem';
        fixture.componentInstance.sampleSolution = 'solution';
        fixture.componentInstance.gradingInstructions = 'grading';
        fixture.detectChanges();

        const markdownBlocks = fixture.nativeElement.querySelectorAll('.markdown-preview');
        expect(markdownBlocks).toHaveLength(3);
        expect(markdownBlocks[0].innerHTML).toContain('converted:problem');
        expect(markdownBlocks[1].innerHTML).toContain('converted:solution');
        expect(markdownBlocks[2].innerHTML).toContain('converted:grading');
    });

    it('should toggle collapse with provided id', () => {
        const fixture = TestBed.createComponent(ResizableInstructionsComponent);
        const toggleSpy = jest.fn();
        fixture.componentInstance.toggleCollapse = toggleSpy;
        fixture.componentInstance.toggleCollapseId = 'instructions';
        fixture.detectChanges();

        fixture.nativeElement.querySelector('.card-header').dispatchEvent(new Event('click'));

        expect(toggleSpy).toHaveBeenCalledWith(expect.any(Event), 'instructions');
    });

    it('should forward criteria and readonly flag to structured layout', () => {
        const fixture = TestBed.createComponent(ResizableInstructionsComponent);
        const criteria = [{ id: 5 } as GradingCriterion];
        fixture.componentInstance.criteria = criteria;
        fixture.componentInstance.readOnly = true;

        fixture.detectChanges();

        const layout = fixture.debugElement.query(By.directive(StructuredLayoutStubComponent)).componentInstance as StructuredLayoutStubComponent;
        expect(layout.criteria).toEqual(criteria);
        expect(layout.readonly).toBeTrue();
    });
});
