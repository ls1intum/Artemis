import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { TemplateRef } from '@angular/core';
import { CourseTitleBarService } from './course-title-bar.service';

describe('CourseTitleBarService', () => {
    setupTestBed({ zoneless: true });

    let service: CourseTitleBarService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [CourseTitleBarService],
        });
        service = TestBed.inject(CourseTitleBarService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
