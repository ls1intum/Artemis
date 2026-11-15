import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { FeatureUsageAttentionComponent } from './feature-usage-attention.component';
import { FeatureUsageStatus, UserFeatureUsage } from '../feature-usage.model';

describe('FeatureUsageAttentionComponent', () => {
    let component: FeatureUsageAttentionComponent;
    let fixture: ComponentFixture<FeatureUsageAttentionComponent>;

    const base: UserFeatureUsage = {
        feature: 'DEIMOS',
        area: 'INTEGRITY',
        status: FeatureUsageStatus.UNUSED,
        noActions: false,
        actionCount: 0,
        viewCount: 0,
        automaticCount: 0,
        systemCount: 0,
        errorCount: 0,
        durationSumMs: 0,
        durationMaxMs: 0,
        activeDays: 0,
        actionDays: 0,
        endpointCount: 1,
        hasActionEndpoints: true,
    };
    const unused = base;
    const onlyAutomatic: UserFeatureUsage = {
        ...base,
        feature: 'HYPERION_VARIANT_GENERATION',
        area: 'AI_AUTHORING',
        status: FeatureUsageStatus.ONLY_AUTOMATIC,
        automaticCount: 690,
        systemCount: 10,
    };
    const viewedOnly: UserFeatureUsage = { ...base, feature: 'FAQ', area: 'COMMUNICATION', status: FeatureUsageStatus.USED, noActions: true, viewCount: 20 };
    const used: UserFeatureUsage = { ...base, feature: 'PROGRAMMING_ONLINE_EDITOR', area: 'PROGRAMMING', status: FeatureUsageStatus.USED, actionCount: 5 };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FeatureUsageAttentionComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(FeatureUsageAttentionComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('features', [used, unused, onlyAutomatic, viewedOnly]);
        fixture.detectChanges();
    });

    it('should put each feature that needs a decision into exactly one section and leave the used ones out', () => {
        const sections = component.sections();

        expect(sections.map((section) => [section.key, section.features.map((feature) => feature.feature)])).toEqual([
            ['onlyAutomatic', ['HYPERION_VARIANT_GENERATION']],
            ['unused', ['DEIMOS']],
            ['noActions', ['FAQ']],
        ]);
        expect(fixture.nativeElement.querySelector('[data-testid="attention-row-PROGRAMMING_ONLINE_EDITOR"]')).toBeNull();
    });

    it('should show the calls that make a feature worth a look, with automatic and system calls apart', () => {
        const automaticRow: HTMLElement = fixture.nativeElement.querySelector('[data-testid="attention-row-HYPERION_VARIANT_GENERATION"]');
        const viewedRow: HTMLElement = fixture.nativeElement.querySelector('[data-testid="attention-row-FAQ"]');

        expect(automaticRow.querySelector('[data-testid="attention-automatic"]')!.textContent!.trim()).toBe('690');
        expect(automaticRow.querySelector('[data-testid="attention-system"]')!.textContent!.trim()).toBe('10');
        expect(viewedRow.querySelector('[data-testid="attention-views"]')!.textContent!.trim()).toBe('20');
        expect(viewedRow.querySelector('[data-testid="attention-automatic"]')).toBeNull();
    });

    it('should say so when a section is empty', () => {
        fixture.componentRef.setInput('features', [used]);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.featureUsage.attention.none');
    });

    it('should ask for the trend of a feature', () => {
        const emitted = vi.fn();
        component.trendRequested.subscribe(emitted);

        (fixture.nativeElement.querySelector('[data-testid="attention-row-FAQ"] button') as HTMLButtonElement).click();

        expect(emitted).toHaveBeenCalledWith(viewedOnly);
    });
});
