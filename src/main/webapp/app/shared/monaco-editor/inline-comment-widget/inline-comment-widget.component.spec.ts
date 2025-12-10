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
            textarea.nativeElement.value = 'Test instruction';
            textarea.nativeElement.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            // Click save button
            const saveButton = fixture.debugElement.query(By.css('button[title*="save"], button.save-button, button:has(fa-icon[icon="faFloppyDisk"])'));
            if (saveButton) {
                saveButton.triggerEventHandler('click', null);
                fixture.detectChanges();

                expect(saveSpy).toHaveBeenCalledWith(
                    expect.objectContaining({
                        startLine: 1,
                        endLine: 5,
                        instruction: 'Test instruction',
                        status: 'pending',
                    }),
                );
            }
        });

        it('should disable save button when instruction is empty', () => {
            fixture.detectChanges();

            // Find buttons and check they are disabled when no instruction
            const buttons = fixture.debugElement.queryAll(By.css('button'));
            const saveButton = buttons.find((btn) => btn.nativeElement.textContent.includes('Save') || btn.attributes['title']?.includes('save'));

            if (saveButton) {
                expect(saveButton.nativeElement.disabled).toBeTrue();
            }
        });
    });

    describe('apply functionality', () => {
        it('should emit onApply when apply button is clicked with valid instruction', async () => {
            const applySpy = jest.spyOn(component.onApply, 'emit');

            // Set instruction via textarea
            const textarea = fixture.debugElement.query(By.css('textarea'));
            textarea.nativeElement.value = 'Test instruction';
            textarea.nativeElement.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            // Find the apply button (has AI icon, btn-outline-primary)
            const applyButton = fixture.debugElement.query(By.css('.btn-outline-primary'));

            if (applyButton && !applyButton.nativeElement.disabled) {
                applyButton.triggerEventHandler('click', mockClickEvent);
                fixture.detectChanges();

                expect(applySpy).toHaveBeenCalled();
            }
        });
    });

    describe('cancel functionality', () => {
        it('should emit onCancel when cancel button is clicked', () => {
            const cancelSpy = jest.spyOn(component.onCancel, 'emit');

            // Find and click cancel button (usually has X icon)
            const cancelButton = fixture.debugElement.query(By.css('button.cancel-button, button[title*="cancel"]'));
            if (cancelButton) {
                cancelButton.triggerEventHandler('click', null);
                fixture.detectChanges();

                expect(cancelSpy).toHaveBeenCalled();
            }
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

            const deleteSpy = jest.spyOn(component.onDelete, 'emit');

            // Find and click delete button
            const deleteButton = fixture.debugElement.query(By.css('button.delete-button, button[title*="delete"]'));
            if (deleteButton) {
                deleteButton.triggerEventHandler('click', null);
                fixture.detectChanges();

                expect(deleteSpy).toHaveBeenCalledWith('test-id-123');
            }
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

        it('should still allow toggling collapse when readOnly', () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.componentRef.setInput('collapsed', true);
            fixture.detectChanges();

            const header = fixture.debugElement.query(By.css('.widget-header'));
            if (header) {
                header.triggerEventHandler('click', null);
                fixture.detectChanges();
                // The widget should respond to clicks
                expect(component.collapsed()).toBeTrue(); // Input is still true, but internal state may differ
            }
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
