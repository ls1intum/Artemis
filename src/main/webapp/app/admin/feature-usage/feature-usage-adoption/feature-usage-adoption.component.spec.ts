import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { FeatureUsageAdoptionComponent } from './feature-usage-adoption.component';
import { FeatureAdoption, FeatureUsageStatus, UserFeatureUsage } from '../feature-usage.model';

describe('FeatureUsageAdoptionComponent', () => {
    let component: FeatureUsageAdoptionComponent;
    let fixture: ComponentFixture<FeatureUsageAdoptionComponent>;

    const feature = (name: string, area: string): UserFeatureUsage => ({
        feature: name,
        area,
        status: FeatureUsageStatus.USED,
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
    });

    const adoption: FeatureAdoption[] = [
        { module: 'quiz', key: 'mode/batched', feature: 'QUIZ_LIVE', count: 1, total: 4 },
        { module: 'programming', key: 'online-ide', feature: 'PROGRAMMING_ONLINE_IDE', count: 0, total: 0 },
        { module: 'programming', key: 'online-editor', feature: 'PROGRAMMING_ONLINE_EDITOR', count: 3, total: 4 },
        { module: 'course', key: 'unknown', count: 1, total: 1 },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FeatureUsageAdoptionComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(FeatureUsageAdoptionComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('adoption', adoption);
        fixture.componentRef.setInput('features', [
            feature('PROGRAMMING_ONLINE_EDITOR', 'PROGRAMMING'),
            feature('PROGRAMMING_ONLINE_IDE', 'PROGRAMMING'),
            feature('QUIZ_LIVE', 'QUIZ'),
        ]);
        fixture.detectChanges();
    });

    it('should list the settings in catalogue order, with the ones of no known feature last', () => {
        expect(component.rows().map((row) => row.entry.key)).toEqual(['online-editor', 'online-ide', 'mode/batched', 'unknown']);
        expect(component.rows().map((row) => row.area)).toEqual(['PROGRAMMING', 'PROGRAMMING', 'QUIZ', undefined]);
    });

    it('should compute the share and not divide by an empty total', () => {
        expect(component.rows()[0].sharePercent).toBe(75);
        expect(component.rows()[1].sharePercent).toBe(0);
    });

    it('should render one row per setting', () => {
        expect(fixture.nativeElement.querySelectorAll('[data-testid^="adoption-row-"]')).toHaveLength(4);
    });
});
