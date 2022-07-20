import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA, DebugElement, Directive, EventEmitter, Input, Output } from '@angular/core';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/shared/markdown-editor/commands/italic.command';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';
import { CodeCommand } from 'app/shared/markdown-editor/commands/code.command';
import { CodeBlockCommand } from 'app/shared/markdown-editor/commands/codeblock.command';
import { LinkCommand } from 'app/shared/markdown-editor/commands/link.command';
import { getElement } from '../../../../helpers/utils/general.utils';
import { By } from '@angular/platform-browser';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { ExerciseReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/exerciseReferenceCommand';
import { LectureAttachmentReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/lectureAttachmentReferenceCommand';
import { metisAnswerPostUser2, metisPostExerciseUser1 } from '../../../../helpers/sample/metis-sample-data';

// tslint:disable-next-line:directive-selector
@Directive({ selector: 'jhi-markdown-editor' })
class MockMarkdownEditorDirective {
    @Input() markdown?: string;
    @Output() markdownChange = new EventEmitter<string>();
}

describe('PostingsMarkdownEditor', () => {
    let component: PostingMarkdownEditorComponent;
    let mockMarkdownEditorDirective: MockMarkdownEditorDirective;
    let fixture: ComponentFixture<PostingMarkdownEditorComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [PostingMarkdownEditorComponent, MockMarkdownEditorDirective],
            schemas: [CUSTOM_ELEMENTS_SCHEMA], // required because we mock the nested MarkdownEditorComponent
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingMarkdownEditorComponent);
                fixture.autoDetectChanges();
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                metisService = TestBed.inject(MetisService);
                const mockMarkdownEditorElement = fixture.debugElement.query(By.directive(MockMarkdownEditorDirective));
                mockMarkdownEditorDirective = mockMarkdownEditorElement.injector.get(MockMarkdownEditorDirective) as MockMarkdownEditorDirective;
                component.ngOnInit();
                component.content = metisPostExerciseUser1.content;
            });
    });

    it('should have set the correct default commands on init', () => {
        component.ngOnInit();

        expect(component.defaultCommands).toEqual([
            new BoldCommand(),
            new ItalicCommand(),
            new UnderlineCommand(),
            new ReferenceCommand(),
            new CodeCommand(),
            new CodeBlockCommand(),
            new LinkCommand(),
            new ExerciseReferenceCommand(metisService),
            new LectureAttachmentReferenceCommand(metisService),
        ]);
    });

    it('should show the correct amount of characters below the markdown input', () => {
        component.maxContentLength = 200;
        fixture.detectChanges();
        const charCounter = getElement(debugElement, 'p.small');
        expect(charCounter.textContent).toContain(component.maxContentLength.toString());
        expect(charCounter.textContent).toContain(metisPostExerciseUser1.content!.length.toString());
        expect(charCounter.style.color).not.toEqual('red');
    });

    it('should show the correct amount of characters in red if max length exceeded', () => {
        component.maxContentLength = 5;
        fixture.detectChanges();
        const charCounter = getElement(debugElement, 'p.small');
        expect(charCounter.textContent).toContain(component.maxContentLength.toString());
        expect(charCounter.textContent).toContain(metisPostExerciseUser1.content!.length.toString());
        expect(charCounter.style.color).toEqual('red');
    });

    it('should initialize markdown correctly with post content', () => {
        component.maxContentLength = 200;
        fixture.detectChanges();
        expect(mockMarkdownEditorDirective.markdown).toEqual(component.content);
    });

    it('should update value if markdown change is emitted', () => {
        component.maxContentLength = 200;
        fixture.detectChanges();
        mockMarkdownEditorDirective.markdownChange.emit('updated text');
        expect(component.content).toEqual('updated text');
    });

    it('should write value of form group in content variable', () => {
        component.writeValue(metisAnswerPostUser2);
        expect(component.content).toEqual(metisAnswerPostUser2);
    });
});
