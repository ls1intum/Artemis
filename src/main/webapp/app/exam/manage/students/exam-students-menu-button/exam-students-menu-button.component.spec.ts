import { ComponentFixture, TestBed } from '@angular/core/testing';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { faPlus, faUser } from '@fortawesome/free-solid-svg-icons';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamStudentsMenuButtonComponent, ExamStudentsMenuItem } from './exam-students-menu-button.component';

describe('ExamStudentsMenuButtonComponent', () => {
    let component: ExamStudentsMenuButtonComponent;
    let fixture: ComponentFixture<ExamStudentsMenuButtonComponent>;
    let firstCommand: Mock<() => void>;
    let dangerCommand: Mock<() => void>;

    const menuEntries = () => Array.from(document.body.querySelectorAll<HTMLElement>('[data-testid="exam-students-menu-entry"]'));

    beforeEach(async () => {
        firstCommand = vi.fn();
        dangerCommand = vi.fn();
        const items: ExamStudentsMenuItem[] = [
            { label: 'Item 1', icon: faPlus, command: firstCommand },
            { label: 'Item 2', disabled: true, tooltip: 'Hint', command: vi.fn() },
            { label: 'Item 3', danger: true, command: dangerCommand },
        ];
        await TestBed.configureTestingModule({
            imports: [ExamStudentsMenuButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsMenuButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('model', items);
        fixture.componentRef.setInput('label', 'Menu Label');
        fixture.componentRef.setInput('buttonIcon', faUser);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create and initialize inputs', () => {
        expect(component).toBeTruthy();
        expect(component.model()).toHaveLength(3);
        expect(component.label()).toBe('Menu Label');
    });

    it('should render no entries until the trigger is clicked', () => {
        expect(menuEntries()).toHaveLength(0);
    });

    it('should list every entry when the trigger is clicked and run the command of a clicked entry', () => {
        fixture.debugElement.query(By.css('button')).nativeElement.click();
        fixture.detectChanges();

        const entries = menuEntries();
        expect(entries).toHaveLength(3);

        entries[0].click();

        expect(firstCommand).toHaveBeenCalledOnce();
    });

    it('should mark a disabled entry as disabled and not run its command', () => {
        fixture.debugElement.query(By.css('button')).nativeElement.click();
        fixture.detectChanges();

        const disabledEntry = menuEntries()[1];
        expect(disabledEntry.getAttribute('aria-disabled')).toBe('true');

        disabledEntry.click();

        expect(component.model()[1].command).not.toHaveBeenCalled();
    });

    it('should render a destructive entry in the danger colour', () => {
        fixture.debugElement.query(By.css('button')).nativeElement.click();
        fixture.detectChanges();

        expect(menuEntries()[2].classList).toContain('text-state-danger');
    });

    it('should not open the menu when the trigger is disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        fixture.debugElement.query(By.css('button')).nativeElement.click();
        fixture.detectChanges();

        expect(menuEntries()).toHaveLength(0);
    });
});
