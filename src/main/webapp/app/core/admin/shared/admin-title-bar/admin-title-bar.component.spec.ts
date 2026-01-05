/**
 * Vitest tests for AdminTitleBarComponent.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { TemplateRef, signal } from '@angular/core';
import { MockDirective } from 'ng-mocks';
import { AdminTitleBarComponent } from './admin-title-bar.component';
import { AdminTitleBarService } from 'app/core/admin/shared/admin-title-bar.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AdminTitleBarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AdminTitleBarComponent;
    let fixture: ComponentFixture<AdminTitleBarComponent>;
    let mockTitleTemplate: ReturnType<typeof signal<TemplateRef<any> | undefined>>;
    let mockActionsTemplate: ReturnType<typeof signal<TemplateRef<any> | undefined>>;

    beforeEach(async () => {
        mockTitleTemplate = signal<TemplateRef<any> | undefined>(undefined);
        mockActionsTemplate = signal<TemplateRef<any> | undefined>(undefined);

        const mockService = {
            titleTemplate: mockTitleTemplate,
            actionsTemplate: mockActionsTemplate,
        };

        await TestBed.configureTestingModule({
            imports: [AdminTitleBarComponent, MockDirective(TranslateDirective)],
            providers: [
                { provide: AdminTitleBarService, useValue: mockService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AdminTitleBarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should have the correct styling classes on the title bar', () => {
        const titleBar = fixture.debugElement.query(By.css('#admin-title-bar'));
        expect(titleBar).toBeTruthy();
        expect(titleBar.classes['module-bg']).toBeTruthy();
        expect(titleBar.classes['rounded']).toBeTruthy();
        expect(titleBar.classes['rounded-3']).toBeTruthy();
        expect(titleBar.classes['d-flex']).toBeTruthy();
        expect(titleBar.classes['justify-content-between']).toBeTruthy();
        expect(titleBar.classes['align-items-center']).toBeTruthy();
    });

    it('should display default title when no custom title template is provided', () => {
        const defaultTitle = fixture.debugElement.query(By.css('h5'));
        expect(defaultTitle).toBeTruthy();
        expect(defaultTitle.attributes['jhiTranslate']).toBe('administration');
    });

    it('should return undefined for customTitleTemplate when service returns undefined', () => {
        mockTitleTemplate.set(undefined);
        fixture.detectChanges();

        expect(component.customTitleTemplate()).toBeUndefined();
    });

    it('should return undefined for customActionsTemplate when service returns undefined', () => {
        mockActionsTemplate.set(undefined);
        fixture.detectChanges();

        expect(component.customActionsTemplate()).toBeUndefined();
    });

    it('should not render actions section when no actions template is provided', () => {
        mockActionsTemplate.set(undefined);
        fixture.detectChanges();

        // The actions container should exist but be empty (no ng-container rendered)
        const actionsContainer = fixture.debugElement.query(By.css('.d-flex.gap-2.align-items-center'));
        expect(actionsContainer).toBeTruthy();
        // No ng-container should be rendered inside
        expect(actionsContainer.children.length).toBe(0);
    });

    it('should have correct structure with title and actions containers', () => {
        const titleBar = fixture.debugElement.query(By.css('#admin-title-bar'));
        expect(titleBar).toBeTruthy();

        // Should have two main child divs - one for title, one for actions
        const children = titleBar.children;
        expect(children.length).toBe(2);

        // First child should be the title container
        expect(children[0].classes['align-self-center']).toBeTruthy();

        // Second child should be the actions container
        expect(children[1].classes['d-flex']).toBeTruthy();
        expect(children[1].classes['gap-2']).toBeTruthy();
        expect(children[1].classes['align-items-center']).toBeTruthy();
    });

    it('should have correct margin and padding classes', () => {
        const titleBar = fixture.debugElement.query(By.css('#admin-title-bar'));
        expect(titleBar).toBeTruthy();
        expect(titleBar.classes['mb-2']).toBeTruthy();
        expect(titleBar.classes['px-3']).toBeTruthy();
        expect(titleBar.classes['py-2']).toBeTruthy();
    });
});
