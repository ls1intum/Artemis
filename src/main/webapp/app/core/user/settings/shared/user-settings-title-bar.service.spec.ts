import { TestBed } from '@angular/core/testing';
import { TemplateRef } from '@angular/core';
import { UserSettingsTitleBarService } from './user-settings-title-bar.service';

describe('UserSettingsTitleBarService', () => {
    let service: UserSettingsTitleBarService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [UserSettingsTitleBarService],
        });
        service = TestBed.inject(UserSettingsTitleBarService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('titleTemplate', () => {
        it('should initially be undefined', () => {
            expect(service.titleTemplate()).toBeUndefined();
        });

        it('should update titleTemplate when setTitleTemplate is called', () => {
            const mockTemplate = {} as TemplateRef<any>;
            service.setTitleTemplate(mockTemplate);
            expect(service.titleTemplate()).toBe(mockTemplate);
        });

        it('should allow setting titleTemplate to undefined', () => {
            const mockTemplate = {} as TemplateRef<any>;
            service.setTitleTemplate(mockTemplate);
            expect(service.titleTemplate()).toBe(mockTemplate);

            service.setTitleTemplate(undefined);
            expect(service.titleTemplate()).toBeUndefined();
        });
    });

    describe('actionsTemplate', () => {
        it('should initially be undefined', () => {
            expect(service.actionsTemplate()).toBeUndefined();
        });

        it('should update actionsTemplate when setActionsTemplate is called', () => {
            const mockTemplate = {} as TemplateRef<any>;
            service.setActionsTemplate(mockTemplate);
            expect(service.actionsTemplate()).toBe(mockTemplate);
        });

        it('should allow setting actionsTemplate to undefined', () => {
            const mockTemplate = {} as TemplateRef<any>;
            service.setActionsTemplate(mockTemplate);
            expect(service.actionsTemplate()).toBe(mockTemplate);

            service.setActionsTemplate(undefined);
            expect(service.actionsTemplate()).toBeUndefined();
        });
    });

    describe('independent title and actions templates', () => {
        it('should manage title and actions templates independently', () => {
            const titleTemplate = {} as TemplateRef<any>;
            const actionsTemplate = {} as TemplateRef<any>;

            service.setTitleTemplate(titleTemplate);
            expect(service.titleTemplate()).toBe(titleTemplate);
            expect(service.actionsTemplate()).toBeUndefined();

            service.setActionsTemplate(actionsTemplate);
            expect(service.titleTemplate()).toBe(titleTemplate);
            expect(service.actionsTemplate()).toBe(actionsTemplate);

            service.setTitleTemplate(undefined);
            expect(service.titleTemplate()).toBeUndefined();
            expect(service.actionsTemplate()).toBe(actionsTemplate);
        });
    });
});
