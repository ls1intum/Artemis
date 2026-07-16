import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { HealthComponent } from 'app/admin/health/health.component';
import { HealthService } from 'app/admin/health/health.service';
import { Health } from 'app/admin/health/health.model';

describe('HealthComponent', () => {
    let comp: HealthComponent;
    let fixture: ComponentFixture<HealthComponent>;
    let healthService: HealthService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HealthComponent],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        })
            .overrideTemplate(HealthComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(HealthComponent);
        comp = fixture.componentInstance;
        healthService = TestBed.inject(HealthService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return success severity for UP status', () => {
        expect(comp.getBadgeSeverity('UP')).toBe('success');
    });

    it('should return danger severity for DOWN status', () => {
        expect(comp.getBadgeSeverity('DOWN')).toBe('danger');
    });

    it('should call refresh on init', () => {
        const health: Health = { status: 'UP', components: { mail: { status: 'UP', details: { mailDetail: 'mail' } } } };
        vi.spyOn(healthService, 'checkHealth').mockReturnValue(of(health));

        comp.ngOnInit();

        expect(healthService.checkHealth).toHaveBeenCalledOnce();
        expect(comp.health()).toEqual(health);
    });

    it('should handle a 503 on refreshing health data', () => {
        const health: Health = { status: 'DOWN', components: { mail: { status: 'DOWN' } } };
        vi.spyOn(healthService, 'checkHealth').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: health })));

        comp.refresh();

        expect(healthService.checkHealth).toHaveBeenCalledOnce();
        expect(comp.health()).toEqual(health);
    });

    it('should ignore malformed 503 health bodies', () => {
        const previous: Health = { status: 'UP', components: { mail: { status: 'UP' } } };
        comp.health.set(previous);
        vi.spyOn(healthService, 'checkHealth').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: 'Service Unavailable' })));

        comp.refresh();

        expect(comp.health()).toEqual(previous);
    });

    it('should ignore incomplete 503 health objects without components', () => {
        vi.spyOn(healthService, 'checkHealth').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { status: 'DOWN' } })));

        comp.refresh();

        expect(comp.health()).toBeUndefined();
    });

    it('should set selectedHealth and show modal when showHealth is called', () => {
        const healthDetails = { key: 'mail', value: { status: 'UP', details: { mailDetail: 'mail' } } };
        comp.showHealth(healthDetails as any);

        expect(comp.selectedHealth()).toEqual(healthDetails);
        expect(comp.showHealthModal()).toBe(true);
    });
});
