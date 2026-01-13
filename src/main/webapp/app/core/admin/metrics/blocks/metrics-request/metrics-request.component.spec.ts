/**
 * Vitest tests for MetricsRequestComponent.
 * Tests the HTTP request metrics display component.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentRef } from '@angular/core';

import { MetricsRequestComponent } from 'app/core/admin/metrics/blocks/metrics-request/metrics-request.component';
import { HttpServerRequests } from 'app/core/admin/metrics/metrics.model';
import { filterNaN } from 'app/core/admin/metrics/filterNaN-util';

describe('MetricsRequestComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MetricsRequestComponent;
    let componentRef: ComponentRef<MetricsRequestComponent>;
    let fixture: ComponentFixture<MetricsRequestComponent>;

    const mockRequestMetrics: HttpServerRequests = {
        all: {
            count: 10000,
        },
        percode: {
            '200': { count: 8500, mean: 35.2, max: 1500 },
            '201': { count: 500, mean: 120.5, max: 800 },
            '400': { count: 200, mean: 15.3, max: 50 },
            '404': { count: 300, mean: 10.1, max: 25 },
            '500': { count: 500, mean: 250.8, max: 2500 },
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetricsRequestComponent],
        })
            .overrideTemplate(MetricsRequestComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(MetricsRequestComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    it('should create component', () => {
        componentRef.setInput('requestMetrics', mockRequestMetrics);
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should accept requestMetrics input', () => {
        componentRef.setInput('requestMetrics', mockRequestMetrics);
        fixture.detectChanges();
        expect(component.requestMetrics()).toEqual(mockRequestMetrics);
    });

    it('should have updating default to false', () => {
        componentRef.setInput('requestMetrics', mockRequestMetrics);
        fixture.detectChanges();
        expect(component.updating()).toBe(false);
    });

    it('should accept updating input as true', () => {
        componentRef.setInput('requestMetrics', mockRequestMetrics);
        componentRef.setInput('updating', true);
        fixture.detectChanges();
        expect(component.updating()).toBe(true);
    });

    it('should have filterNaN utility available', () => {
        componentRef.setInput('requestMetrics', mockRequestMetrics);
        fixture.detectChanges();
        expect(component['filterNaN']).toBe(filterNaN);
    });

    it('should handle metrics with only success codes', () => {
        const successOnlyMetrics: HttpServerRequests = {
            all: { count: 5000 },
            percode: {
                '200': { count: 4500, mean: 28.5, max: 400 },
                '201': { count: 500, mean: 45.2, max: 500 },
            },
        };
        componentRef.setInput('requestMetrics', successOnlyMetrics);
        fixture.detectChanges();
        expect(Object.keys(component.requestMetrics().percode)).toHaveLength(2);
    });

    it('should handle metrics with zero requests', () => {
        const emptyMetrics: HttpServerRequests = {
            all: { count: 0 },
            percode: {},
        };
        componentRef.setInput('requestMetrics', emptyMetrics);
        fixture.detectChanges();
        expect(component.requestMetrics().all.count).toBe(0);
        expect(Object.keys(component.requestMetrics().percode)).toHaveLength(0);
    });

    it('should handle metrics with high error rates', () => {
        const highErrorMetrics: HttpServerRequests = {
            all: { count: 1000 },
            percode: {
                '200': { count: 200, mean: 25.0, max: 100 },
                '500': { count: 800, mean: 125.0, max: 3000 },
            },
        };
        componentRef.setInput('requestMetrics', highErrorMetrics);
        fixture.detectChanges();
        expect(component.requestMetrics().percode['500'].count).toBe(800);
    });
});
