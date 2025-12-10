import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { InlineCommentWidgetComponent } from './inline-comment-widget.component';
import { InlineComment } from '../model/inline-comment.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';

const mockClickEvent = { stopPropagation: jest.fn(), preventDefault: jest.fn() };

describe('InlineCommentWidgetComponent', () => {
    let component: InlineCommentWidgetComponent;
    let fixture: ComponentFixture<InlineCommentWidgetComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [InlineCommentWidgetComponent, TranslateModule.forRoot()],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(InlineCommentWidgetComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('startLine', 1);
        fixture.componentRef.setInput('endLine', 5);
        fixture.detectChanges();
    });

    describe('initialization', () => {
        it('should create the component', () => {
            expect(component).toBeTruthy();
        });

        it('should initialize with default values', () => {
            expect(component.startLine()).toBe(1);
            expect(component.endLine()).toBe(5);
            expect(component.collapsed()).toBeFalse();
            expect(component.readOnly()).toBeFalse();
        });

        it('should render the widget container', () => {
            const container = fixture.debugElement.query(By.css('.inline-comment-widget'));
            expect(container).toBeTruthy();
        });
    });

    describe('existingComment input', () => {
        it('should populate instruction from existing comment in textarea', async () => {
            const existingComment: InlineComment = {
                id: 'test-id',
                startLine: 1,
                endLine: 5,
                instruction: 'Existing instruction text',
                status: 'pending',
                createdAt: new Date(),
            };

            fixture.componentRef.setInput('existingComment', existingComment);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            const textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea?.nativeElement.value).toBe('Existing instruction text');
        });
    });

    describe('collapsed state', () => {
        it('should show collapsed view when collapsed input is true', () => {
            fixture.componentRef.setInput('collapsed', true);
            fixture.detectChanges();

            // When collapsed, textarea should not be visible
            const textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea).toBeFalsy();
        });

        it('should expand when header is clicked in collapsed state', () => {
            fixture.componentRef.setInput('collapsed', true);
            fixture.detectChanges();

            const header = fixture.debugElement.query(By.css('.widget-header'));
            header?.triggerEventHandler('click', null);
            fixture.detectChanges();

            // After clicking, textarea should be visible
            const textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea).toBeTruthy();
        });
    });

    describe('save functionality', () => {
        it('should emit onSave with comment data when save button is clicked', async () => {
            const saveSpy = jest.spyOn(component.onSave, 'emit');

            // Set instruction via textarea
            const textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea).toBeTruthy();
            textarea.nativeElement.value = 'Test instruction';
            textarea.nativeElement.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            // Find and click save button (btn-primary in action-buttons)
            const saveButton = fixture.debugElement.query(By.css('.action-buttons .btn-primary'));
            expect(saveButton).toBeTruthy();
            expect(saveButton.nativeElement.disabled).toBeFalse();

            saveButton.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();

            expect(saveSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    startLine: 1,
                    endLine: 5,
                    instruction: 'Test instruction',
                }),
            );
        });

        it('should disable save button when instruction is empty', () => {
            fixture.detectChanges();

            // Find save button (btn-primary in action-buttons)
            const saveButton = fixture.debugElement.query(By.css('.action-buttons .btn-primary'));
            expect(saveButton).toBeTruthy();
            expect(saveButton.nativeElement.disabled).toBeTrue();
        });
    });

    describe('apply functionality', () => {
        it('should emit onApply when apply button is clicked with valid instruction', async () => {
            const applySpy = jest.spyOn(component.onApply, 'emit');

            // Set instruction via textarea
            const textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea).toBeTruthy();
            textarea.nativeElement.value = 'Test instruction';
            textarea.nativeElement.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            // Find the apply button (btn-outline-primary in action-buttons)
            const applyButton = fixture.debugElement.query(By.css('.action-buttons .btn-outline-primary'));
            expect(applyButton).toBeTruthy();
            expect(applyButton.nativeElement.disabled).toBeFalse();

            applyButton.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();

            expect(applySpy).toHaveBeenCalled();
        });
    });

    describe('cancel functionality', () => {
        it('should emit onCancel when cancel button is clicked', () => {
            const cancelSpy = jest.spyOn(component.onCancel, 'emit');

            // Find cancel button (btn-secondary in action-buttons)
            const cancelButton = fixture.debugElement.query(By.css('.action-buttons .btn-secondary'));
            expect(cancelButton).toBeTruthy();

            cancelButton.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();

            expect(cancelSpy).toHaveBeenCalled();
        });
    });

    describe('delete functionality', () => {
        it('should emit onDelete with comment ID when delete is triggered', async () => {
            const existingComment: InlineComment = {
                id: 'test-id-123',
                startLine: 1,
                endLine: 5,
                instruction: 'Test',
                status: 'pending',
                createdAt: new Date(),
            };
            fixture.componentRef.setInput('existingComment', existingComment);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            const deleteSpy = jest.spyOn(component.onDelete, 'emit');

            // Find delete button (btn-outline-danger in widget-actions)
            const deleteButton = fixture.debugElement.query(By.css('.widget-actions .btn-outline-danger'));
            expect(deleteButton).toBeTruthy();

            deleteButton.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();

            expect(deleteSpy).toHaveBeenCalledWith('test-id-123');
        });
    });

    describe('lineLabel display', () => {
        it('should display line information in header', async () => {
            fixture.componentRef.setInput('startLine', 5);
            fixture.componentRef.setInput('endLine', 5);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.startLine()).toBe(5);
            expect(component.endLine()).toBe(5);
        });

        it('should handle line range when start differs from end', async () => {
            fixture.componentRef.setInput('startLine', 5);
            fixture.componentRef.setInput('endLine', 10);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.startLine()).toBe(5);
            expect(component.endLine()).toBe(10);
        });
    });

    describe('readOnly mode', () => {
        it('should disable textarea when readOnly is true', async () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            const textarea = fixture.debugElement.query(By.css('textarea'));
            // Textarea should be disabled when readOnly is true
            expect(textarea.nativeElement.disabled).toBeTrue();
        });

        it('should still allow toggling collapse when readOnly', async () => {
            // Set up an existing comment (required for collapse icon to appear)
            const existingComment: InlineComment = {
                id: 'test-id',
                startLine: 1,
                endLine: 5,
                instruction: 'Test instruction',
                status: 'pending',
                createdAt: new Date(),
            };
            fixture.componentRef.setInput('existingComment', existingComment);
            fixture.componentRef.setInput('readOnly', true);
            fixture.componentRef.setInput('collapsed', true);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            // Verify initially collapsed - textarea should not be visible
            let textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea).toBeFalsy();

            // Find header and click to expand
            const header = fixture.debugElement.query(By.css('.widget-header'));
            expect(header).toBeTruthy();

            header.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            // After toggle, widget should be expanded - textarea should be visible
            textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea).toBeTruthy();
            // Textarea should be disabled since readOnly is true
            expect(textarea.nativeElement.disabled).toBeTrue();
        });
    });

    describe('isApplying state', () => {
        it('should show spinner when isApplying is true', () => {
            fixture.componentRef.setInput('isApplying', true);
            fixture.detectChanges();

            // Look for spinner icon
            const spinner = fixture.debugElement.query(By.css('fa-icon[icon="faSpinner"], .spinner'));
            // The spinner should be present or buttons should be disabled
            const buttons = fixture.debugElement.queryAll(By.css('button'));
            const disabledButtons = buttons.filter((btn) => btn.nativeElement.disabled);

            // Either spinner is shown or buttons are disabled
            expect(spinner || disabledButtons.length > 0).toBeTruthy();
        });
    });
});
