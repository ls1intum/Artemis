/**
 * Vitest tests for AdminTitleBarService.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TemplateRef } from '@angular/core';
import { AdminTitleBarService } from './admin-title-bar.service';

describe('AdminTitleBarService', () => {
    setupTestBed({ zoneless: true });

    let service: AdminTitleBarService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AdminTitleBarService],
        });
        service = TestBed.inject(AdminTitleBarService);
    });

    it('should have undefined titleTemplate and actionsTemplate by default', () => {
        expect(service.titleTemplate()).toBeUndefined();
        expect(service.actionsTemplate()).toBeUndefined();
    });

    describe('titleTemplate', () => {
        it('should set and get the titleTemplate signal', () => {
            const fakeTpl = {} as TemplateRef<any>;

            service.setTitleTemplate(fakeTpl);
            expect(service.titleTemplate()).toBe(fakeTpl);

            service.setTitleTemplate(undefined);
            expect(service.titleTemplate()).toBeUndefined();
        });

        it('should overwrite an existing titleTemplate when setTitleTemplate is called again', () => {
            const firstTpl = {} as TemplateRef<any>;
            const secondTpl = {} as TemplateRef<any>;

            service.setTitleTemplate(firstTpl);
            expect(service.titleTemplate()).toBe(firstTpl);

            service.setTitleTemplate(secondTpl);
            expect(service.titleTemplate()).toBe(secondTpl);
        });
    });

    describe('actionsTemplate', () => {
        it('should set and get the actionsTemplate signal', () => {
            const fakeTpl = {} as TemplateRef<any>;

            service.setActionsTemplate(fakeTpl);
            expect(service.actionsTemplate()).toBe(fakeTpl);

            service.setActionsTemplate(undefined);
            expect(service.actionsTemplate()).toBeUndefined();
        });

        it('should overwrite an existing actionsTemplate when setActionsTemplate is called again', () => {
            const firstTpl = {} as TemplateRef<any>;
            const secondTpl = {} as TemplateRef<any>;

            service.setActionsTemplate(firstTpl);
            expect(service.actionsTemplate()).toBe(firstTpl);

            service.setActionsTemplate(secondTpl);
            expect(service.actionsTemplate()).toBe(secondTpl);
        });
    });
});
