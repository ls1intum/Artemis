/**
 * Vitest tests for MetricsComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { MetricsComponent } from 'app/core/admin/metrics/metrics.component';
import { MetricsService } from 'app/core/admin/metrics/metrics.service';
import { Metrics, ThreadDump } from 'app/core/admin/metrics/metrics.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('MetricsComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: MetricsComponent;
    let fixture: ComponentFixture<MetricsComponent>;
    let service: MetricsService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetricsComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        })
            .overrideTemplate(MetricsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(MetricsComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(MetricsService);
    });

    it('should call refresh on init', () => {
        vi.spyOn(service, 'getMetrics').mockReturnValue(of({} as Metrics));
        vi.spyOn(service, 'threadDump').mockReturnValue(of({ threads: [] } as ThreadDump));

        comp.ngOnInit();

        expect(service.getMetrics).toHaveBeenCalledOnce();
    });

    it('should load metrics and thread dump on init', () => {
        const mockMetrics = {};
        const mockThreadDump = { threads: [] };
        vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics as Metrics));
        vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump as ThreadDump));

        expect(comp.updatingMetrics()).toBe(true);

        comp.ngOnInit();

        expect(service.getMetrics).toHaveBeenCalledOnce();
        expect(service.threadDump).toHaveBeenCalledOnce();
        expect(comp.updatingMetrics()).toBe(false);
        expect(comp.metrics()).toEqual(mockMetrics);
        expect(comp.threads()).toEqual(mockThreadDump.threads);
    });

    it('metricsKeyExists method should return false for undefined key', () => {
        comp.metrics.set({} as Metrics);
        expect(comp.metricsKeyExists('cache')).toBe(false);
    });

    it('metricsKeyExists method should return false for undefined value', () => {
        comp.metrics.set({ cache: undefined } as unknown as Metrics);
        expect(comp.metricsKeyExists('cache')).toBe(false);
    });

    it('metricsKeyExists method should return true for defined value', () => {
        comp.metrics.set({ cache: {} } as unknown as Metrics);
        expect(comp.metricsKeyExists('cache')).toBe(true);
    });

    it('metricsKeyExistsAndObjectNotEmpty should return false for undefined value', () => {
        comp.metrics.set({ cache: undefined } as unknown as Metrics);
        expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBe(false);
    });

    it('metricsKeyExistsAndObjectNotEmpty should return false for empty object', () => {
        comp.metrics.set({ cache: {} } as unknown as Metrics);
        expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBe(false);
    });

    it('metricsKeyExistsAndObjectNotEmpty should return true for non-empty object', () => {
        comp.metrics.set({ cache: { randomKey: {} } } as unknown as Metrics);
        expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBe(true);
    });
});
