import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { InlineCommentWidgetComponent } from './inline-comment-widget.component';
import { InlineComment } from '../model/inline-comment.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';

const mockClickEvent = { stopPropagation: jest.fn(), preventDefault: jest.fn() };

/** Creates a mock existing comment for testing */
const createMockComment = (id: string, instruction = 'Test'): InlineComment => ({
    id,
    startLine: 1,
    endLine: 5,
    instruction,
    status: 'pending',
    createdAt: new Date(),
});

describe('InlineCommentWidgetComponent', () => {
    let component: InlineCommentWidgetComponent;
    let fixture: ComponentFixture<InlineCommentWidgetComponent>;

    /** Helper to set textarea value and trigger change detection */
    const setInstruction = async (text: string) => {
        const textarea = fixture.debugElement.query(By.css('textarea'));
        textarea.nativeElement.value = text;
        textarea.nativeElement.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

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
        it('should create with default values and render container', () => {
            expect(component).toBeTruthy();
            expect(component.startLine()).toBe(1);
            expect(component.endLine()).toBe(5);
            expect(component.collapsed()).toBeFalse();
            expect(component.readOnly()).toBeFalse();
            expect(fixture.debugElement.query(By.css('.inline-comment-widget'))).toBeTruthy();
        });
    });

    describe('existingComment input', () => {
        it('should populate instruction from existing comment', async () => {
            fixture.componentRef.setInput('existingComment', createMockComment('test-id', 'Existing instruction'));
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            const textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea?.nativeElement.value).toBe('Existing instruction');
        });
    });

    describe('collapsed state', () => {
        it('should hide textarea when collapsed and show after header click', () => {
            fixture.componentRef.setInput('collapsed', true);
            fixture.detectChanges();
            expect(fixture.debugElement.query(By.css('textarea'))).toBeFalsy();

            const header = fixture.debugElement.query(By.css('.widget-header'));
            header?.triggerEventHandler('click', null);
            fixture.detectChanges();

            expect(fixture.debugElement.query(By.css('textarea'))).toBeTruthy();
        });
    });

    describe('button actions', () => {
        it('should emit onSave with comment data when save button clicked', async () => {
            const saveSpy = jest.spyOn(component.onSave, 'emit');
            await setInstruction('Test instruction');

            const saveButton = fixture.debugElement.query(By.css('.action-buttons .btn-primary'));
            expect(saveButton).toBeTruthy();
            expect(saveButton.nativeElement.disabled).toBeFalse();

            saveButton.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();

            expect(saveSpy).toHaveBeenCalledWith(expect.objectContaining({ startLine: 1, endLine: 5, instruction: 'Test instruction' }));
        });

        it('should disable save/apply buttons when instruction is empty', () => {
            fixture.detectChanges();
            const saveButton = fixture.debugElement.query(By.css('.action-buttons .btn-primary'));
            expect(saveButton.nativeElement.disabled).toBeTrue();
        });

        it('should emit onApply when apply button clicked', async () => {
            const applySpy = jest.spyOn(component.onApply, 'emit');
            await setInstruction('Test instruction');

            const applyButton = fixture.debugElement.query(By.css('.action-buttons .btn-outline-primary'));
            expect(applyButton).toBeTruthy();
            expect(applyButton.nativeElement.disabled).toBeFalse();

            applyButton.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();

            expect(applySpy).toHaveBeenCalled();
        });

        it('should emit onCancel when cancel button clicked', () => {
            const cancelSpy = jest.spyOn(component.onCancel, 'emit');
            const cancelButton = fixture.debugElement.query(By.css('.action-buttons .btn-secondary'));
            expect(cancelButton).toBeTruthy();

            cancelButton.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();

            expect(cancelSpy).toHaveBeenCalled();
        });

        it('should emit onDelete with comment ID when delete clicked', async () => {
            fixture.componentRef.setInput('existingComment', createMockComment('test-id-123'));
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            const deleteSpy = jest.spyOn(component.onDelete, 'emit');
            const deleteButton = fixture.debugElement.query(By.css('.widget-actions .btn-outline-danger'));
            expect(deleteButton).toBeTruthy();

            deleteButton.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();

            expect(deleteSpy).toHaveBeenCalledWith('test-id-123');
        });
    });

    describe('readOnly mode', () => {
        it('should disable textarea and still allow collapse toggle', async () => {
            fixture.componentRef.setInput('existingComment', createMockComment('test-id', 'Test instruction'));
            fixture.componentRef.setInput('readOnly', true);
            fixture.componentRef.setInput('collapsed', true);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            // Initially collapsed
            expect(fixture.debugElement.query(By.css('textarea'))).toBeFalsy();

            // Click to expand
            const header = fixture.debugElement.query(By.css('.widget-header'));
            expect(header).toBeTruthy();
            header.triggerEventHandler('click', mockClickEvent);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            // Expanded but disabled
            const textarea = fixture.debugElement.query(By.css('textarea'));
            expect(textarea).toBeTruthy();
            expect(textarea.nativeElement.disabled).toBeTrue();
        });
    });

    describe('isApplying state', () => {
        it('should disable buttons when isApplying is true', () => {
            fixture.componentRef.setInput('isApplying', true);
            fixture.detectChanges();

            const buttons = fixture.debugElement.queryAll(By.css('button'));
            const disabledButtons = buttons.filter((btn) => btn.nativeElement.disabled);
            expect(disabledButtons.length).toBeGreaterThan(0);
        });
    });
});
