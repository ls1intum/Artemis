/**
 * Vitest tests for LogsComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { LogsComponent } from 'app/core/admin/logs/logs.component';
import { LogsService } from 'app/core/admin/logs/logs.service';
import { Log, LoggersResponse } from 'app/core/admin/logs/log.model';

describe('LogsComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: LogsComponent;
    let fixture: ComponentFixture<LogsComponent>;
    let service: LogsService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LogsComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), LogsService],
        })
            .overrideTemplate(LogsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(LogsComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(LogsService);
    });

    it('should set all default values correctly', () => {
        expect(comp.filter()).toBe('');
        expect(comp.orderProp()).toBe('name');
        expect(comp.ascending()).toBe(true);
    });

    it('should call load all on init', () => {
        const log = new Log('main', 'WARN');
        vi.spyOn(service, 'findAll').mockReturnValue(
            of({
                loggers: {
                    main: {
                        effectiveLevel: 'WARN',
                    },
                },
            } as unknown as LoggersResponse),
        );

        comp.ngOnInit();

        expect(service.findAll).toHaveBeenCalledOnce();
        expect(comp.loggers()[0]).toEqual(expect.objectContaining(log));
    });

    it('should change log level correctly', () => {
        const log = new Log('main', 'ERROR');
        vi.spyOn(service, 'changeLevel').mockReturnValue(of({}));
        vi.spyOn(service, 'findAll').mockReturnValue(
            of({
                loggers: {
                    main: {
                        effectiveLevel: 'ERROR',
                    },
                },
            } as unknown as LoggersResponse),
        );

        comp.changeLevel('main', 'ERROR');

        expect(service.changeLevel).toHaveBeenCalledOnce();
        expect(service.findAll).toHaveBeenCalledOnce();
        expect(comp.loggers()[0]).toEqual(expect.objectContaining(log));
    });

    it('should filter loggers correctly', () => {
        // Set up initial loggers via service mock
        vi.spyOn(service, 'findAll').mockReturnValue(
            of({
                loggers: {
                    footestbar: { effectiveLevel: 'DEBUG' },
                    somethingelse: { effectiveLevel: 'DEBUG' },
                },
            } as unknown as LoggersResponse),
        );
        comp.ngOnInit();

        // Apply filter - computed signal updates automatically
        comp.filter.set('test');

        expect(comp.filteredAndOrderedLoggers()).toEqual([{ name: 'footestbar', level: 'DEBUG' }]);
    });

    it('should sort loggers by name correctly', () => {
        vi.spyOn(service, 'findAll').mockReturnValue(
            of({
                loggers: {
                    test2: { effectiveLevel: 'DEBUG' },
                    test1: { effectiveLevel: 'WARN' },
                    test3: { effectiveLevel: 'WARN' },
                },
            } as unknown as LoggersResponse),
        );
        comp.ngOnInit();

        // orderProp is already 'name' by default, computed signal updates automatically
        comp.orderProp.set('name');

        const sorted = comp.filteredAndOrderedLoggers();
        expect(sorted[0].name).toBe('test1');
        expect(sorted[1].name).toBe('test2');
        expect(sorted[2].name).toBe('test3');
    });

    it('should sort loggers by level correctly', () => {
        vi.spyOn(service, 'findAll').mockReturnValue(
            of({
                loggers: {
                    test1: { effectiveLevel: 'WARN' },
                    test2: { effectiveLevel: 'TRACE' },
                },
            } as unknown as LoggersResponse),
        );
        comp.ngOnInit();

        // Computed signal updates automatically when orderProp changes
        comp.orderProp.set('level');

        expect(comp.filteredAndOrderedLoggers()).toEqual([
            { name: 'test2', level: 'TRACE' },
            { name: 'test1', level: 'WARN' },
        ]);
    });

    it('should sort by name when levels are equal', () => {
        vi.spyOn(service, 'findAll').mockReturnValue(
            of({
                loggers: {
                    test2: { effectiveLevel: 'WARN' },
                    test1: { effectiveLevel: 'WARN' },
                },
            } as unknown as LoggersResponse),
        );
        comp.ngOnInit();

        // Computed signal updates automatically when orderProp changes
        comp.orderProp.set('level');

        expect(comp.filteredAndOrderedLoggers()).toEqual([
            { name: 'test1', level: 'WARN' },
            { name: 'test2', level: 'WARN' },
        ]);
    });

    it('should update filter via updateFilter method', () => {
        comp.updateFilter('testFilter');

        expect(comp.filter()).toBe('testFilter');
    });

    it('should update sort via updateSort method', () => {
        comp.updateSort('level', false);

        expect(comp.orderProp()).toBe('level');
        expect(comp.ascending()).toBe(false);
    });

    it('should sort loggers in descending order', () => {
        vi.spyOn(service, 'findAll').mockReturnValue(
            of({
                loggers: {
                    test1: { effectiveLevel: 'DEBUG' },
                    test2: { effectiveLevel: 'WARN' },
                    test3: { effectiveLevel: 'ERROR' },
                },
            } as unknown as LoggersResponse),
        );
        comp.ngOnInit();

        // Set descending order
        comp.ascending.set(false);

        const sorted = comp.filteredAndOrderedLoggers();
        expect(sorted[0].name).toBe('test3');
        expect(sorted[1].name).toBe('test2');
        expect(sorted[2].name).toBe('test1');
    });

    it('should filter case insensitively', () => {
        vi.spyOn(service, 'findAll').mockReturnValue(
            of({
                loggers: {
                    TestLogger: { effectiveLevel: 'DEBUG' },
                    anotherlogger: { effectiveLevel: 'WARN' },
                },
            } as unknown as LoggersResponse),
        );
        comp.ngOnInit();

        comp.filter.set('TEST');

        expect(comp.filteredAndOrderedLoggers()).toHaveLength(1);
        expect(comp.filteredAndOrderedLoggers()[0].name).toBe('TestLogger');
    });
});
