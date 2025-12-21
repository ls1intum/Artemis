/**
 * Vitest tests for ConfigurationComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { ConfigurationComponent } from 'app/core/admin/configuration/configuration.component';
import { ConfigurationService } from 'app/core/admin/configuration/configuration.service';
import { Bean, PropertySource } from 'app/core/admin/configuration/configuration.model';

describe('ConfigurationComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ConfigurationComponent;
    let fixture: ComponentFixture<ConfigurationComponent>;
    let service: ConfigurationService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ConfigurationComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), ConfigurationService],
        })
            .overrideTemplate(ConfigurationComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ConfigurationComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ConfigurationService);
    });

    it('should load beans and property sources on init', () => {
        const beans: Bean[] = [
            {
                prefix: 'jhipster',
                properties: {
                    clientApp: {
                        name: 'jhipsterApp',
                    },
                },
            },
        ];
        const propertySources: PropertySource[] = [
            {
                name: 'server.ports',
                properties: {
                    'local.server.port': {
                        value: '8080',
                    },
                },
            },
        ];
        vi.spyOn(service, 'getBeans').mockReturnValue(of(beans));
        vi.spyOn(service, 'getPropertySources').mockReturnValue(of(propertySources));

        comp.ngOnInit();

        expect(service.getBeans).toHaveBeenCalledOnce();
        expect(service.getPropertySources).toHaveBeenCalledOnce();
        // beans() returns the filtered/sorted signal value
        expect(comp.beans()).toEqual(beans);
        expect(comp.propertySources()).toEqual(propertySources);
    });

    it('should filter beans by prefix', () => {
        const allBeans: Bean[] = [
            { prefix: 'jhipster', properties: {} },
            { prefix: 'spring', properties: {} },
            { prefix: 'jhipster-test', properties: {} },
        ];
        // Mock the service and initialize to load beans
        vi.spyOn(service, 'getBeans').mockReturnValue(of(allBeans));
        vi.spyOn(service, 'getPropertySources').mockReturnValue(of([]));
        comp.ngOnInit();

        // Now apply filter - computed signal updates automatically
        comp.beansFilter.set('jhipster');

        expect(comp.beans()).toHaveLength(2);
        expect(comp.beans().every((b) => b.prefix.includes('jhipster'))).toBe(true);
    });

    it('should sort beans in ascending order by default', () => {
        const allBeans: Bean[] = [
            { prefix: 'spring', properties: {} },
            { prefix: 'artemis', properties: {} },
            { prefix: 'jhipster', properties: {} },
        ];
        vi.spyOn(service, 'getBeans').mockReturnValue(of(allBeans));
        vi.spyOn(service, 'getPropertySources').mockReturnValue(of([]));
        comp.ngOnInit();

        // Computed signal updates automatically when beansAscending changes
        comp.beansAscending.set(true);

        expect(comp.beans()[0].prefix).toBe('artemis');
        expect(comp.beans()[1].prefix).toBe('jhipster');
        expect(comp.beans()[2].prefix).toBe('spring');
    });

    it('should sort beans in descending order when beansAscending is false', () => {
        const allBeans: Bean[] = [
            { prefix: 'spring', properties: {} },
            { prefix: 'artemis', properties: {} },
            { prefix: 'jhipster', properties: {} },
        ];
        vi.spyOn(service, 'getBeans').mockReturnValue(of(allBeans));
        vi.spyOn(service, 'getPropertySources').mockReturnValue(of([]));
        comp.ngOnInit();

        // Computed signal updates automatically when beansAscending changes
        comp.beansAscending.set(false);

        expect(comp.beans()[0].prefix).toBe('spring');
        expect(comp.beans()[1].prefix).toBe('jhipster');
        expect(comp.beans()[2].prefix).toBe('artemis');
    });
});
