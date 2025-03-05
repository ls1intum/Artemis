import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { By } from '@angular/platform-browser';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { TreeViewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/tree-view-item';

describe('CodeEditorFileBrowserFileComponent', () => {
    let component: CodeEditorFileBrowserFileComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserFileComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, MockPipe(ArtemisTranslatePipe)],
            declarations: [CodeEditorFileBrowserFileComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(CodeEditorFileBrowserFileComponent);
        component = fixture.componentInstance;
        component.disableActions = false;
        component.item = { value: 'TestFile', checked: true } as unknown as TreeViewItem<string>;
        fixture.detectChanges();
    });

    describe('Reopen Feedback Button', () => {
        it('should render the reopen feedback button with correct id and attributes', () => {
            const reopenButton = fixture.debugElement.query(By.css('#file-browser-reopen-feedback')).parent!.nativeElement;

            expect(reopenButton).not.toBeNull();
            expect(reopenButton.classList).toContain('btn');
            expect(reopenButton.classList).toContain('btn-small');
        });

        it('should render the correct icon for reopen feedback button', () => {
            const iconElement = fixture.debugElement.query(By.css('#file-browser-reopen-feedback')).nativeElement;

            expect(iconElement).not.toBeNull();
            expect(component.faEye.iconName).toBe('eye');
        });

        it('should call reopenFeedback when the reopen feedback button is clicked', () => {
            jest.spyOn(component, 'reopenFeedback');
            const reopenButton = fixture.debugElement.query(By.css('#file-browser-reopen-feedback')).parent!.nativeElement;

            reopenButton.click();
            expect(component.reopenFeedback).toHaveBeenCalled();
        });

        it('should stop propagation of the click event', () => {
            const mockEvent = { stopPropagation: jest.fn() } as any;
            jest.spyOn(mockEvent, 'stopPropagation');
            component.reopenFeedback(mockEvent);

            expect(mockEvent.stopPropagation).toHaveBeenCalledOnce();
        });

        it('should emit onReopenFeedbackNode event with correct item', () => {
            const mockEvent = { stopPropagation: jest.fn() } as any;
            jest.spyOn(component.onReopenFeedbackNode, 'emit');

            component.reopenFeedback(mockEvent);

            expect(component.onReopenFeedbackNode.emit).toHaveBeenCalledWith(component.item);
        });
    });
});
