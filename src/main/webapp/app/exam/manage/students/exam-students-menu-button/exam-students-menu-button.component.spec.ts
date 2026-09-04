import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamStudentsMenuButtonComponent } from './exam-students-menu-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MenuItem } from 'primeng/api';
import { By } from '@angular/platform-browser';

describe('ExamStudentsMenuButtonComponent', () => {
    let component: ExamStudentsMenuButtonComponent;
    let fixture: ComponentFixture<ExamStudentsMenuButtonComponent>;

    const items: MenuItem[] = [{ label: 'Item 1', icon: 'pi pi-plus' }];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamStudentsMenuButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsMenuButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('model', items);
        fixture.componentRef.setInput('label', 'Menu Label');
        fixture.componentRef.setInput('buttonIconClass', 'fa fa-user');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create and initialize inputs', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
        expect(component.model()).toEqual(items);
        expect(component.label()).toBe('Menu Label');
        expect(component.buttonIconClass()).toBe('fa fa-user');
    });

    it('should trigger toggleMenu when button is clicked in template', () => {
        fixture.detectChanges();
        const toggleSpy = vi.spyOn(component, 'toggleMenu');
        const button = fixture.debugElement.query(By.css('button'));
        expect(button).not.toBeNull();

        button.nativeElement.click();

        expect(toggleSpy).toHaveBeenCalledOnce();
    });

    it('should toggle menu on toggleMenu', () => {
        fixture.detectChanges();
        const menu = component.menu();
        expect(menu).not.toBeNull();
        const toggleSpy = vi.spyOn(menu!, 'toggle');

        const mockEvent = new MouseEvent('click');
        component.toggleMenu(mockEvent);

        expect(toggleSpy).toHaveBeenCalledOnce();
        expect(toggleSpy).toHaveBeenCalledWith(mockEvent);
    });
});
