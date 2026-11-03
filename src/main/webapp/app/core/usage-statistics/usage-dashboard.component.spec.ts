import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { UsageDashboardComponent } from 'app/core/usage-statistics/usage-dashboard.component';
import { PublicUsageStatistics } from 'app/core/usage-statistics/usage-statistics.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import enUsageDashboard from 'src/main/webapp/i18n/en/usageDashboard.json';
import deUsageDashboard from 'src/main/webapp/i18n/de/usageDashboard.json';

describe('UsageDashboardComponent', () => {
    let fixture: ComponentFixture<UsageDashboardComponent>;
    let httpMock: HttpTestingController;

    const statistics: PublicUsageStatistics = {
        generatedAt: '2026-11-01T10:00:00Z',
        semesters: [
            {
                label: 'WS 2026/27',
                courses: 12,
                students: 3400,
                activeStudents: 2000,
                submissions: 150000,
                lectures: 80,
                exercisesByType: { programming: 40, quiz: 10, text: 5, modeling: 3, 'file-upload': 2 },
                builds: { count: 25000, averageDurationSeconds: 41.6 },
                communication: { posts: 900, replies: 1800, reactions: 3200, faqs: 14 },
                irisMessages: 5400,
                tutorialGroups: 36,
            },
            {
                label: 'SS 2026',
                courses: 8,
                students: 2000,
                activeStudents: 1000,
                submissions: 90000,
                lectures: 50,
                exercisesByType: { programming: 20, quiz: 0, text: 0, modeling: 0, 'file-upload': 0 },
                builds: { count: 0 },
                communication: { posts: 0, replies: 0, reactions: 0, faqs: 0 },
            },
        ],
    };

    const text = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)?.textContent ?? '';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        });
        httpMock = TestBed.inject(HttpTestingController);
        fixture = TestBed.createComponent(UsageDashboardComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('requests the public endpoint and shows the newest semester first', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        expect(text('usage-courses')).toContain('12');
        expect(text('usage-students')).toContain('3,400');
        expect(text('usage-active-students')).toContain('2,000');
        expect(text('usage-submissions')).toContain('150,000');
        expect(text('usage-exercises')).toContain('60');
        expect(text('usage-lectures')).toContain('80');
    });

    it('shows builds, tutorial groups, communication and Iris messages', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        expect(text('usage-builds')).toContain('25,000');
        expect(text('usage-build-duration')).toContain('42 s');
        expect(text('usage-tutorial-groups')).toContain('36');
        expect(text('usage-posts')).toContain('900');
        expect(text('usage-replies')).toContain('1,800');
        expect(text('usage-reactions')).toContain('3,200');
        expect(text('usage-faqs')).toContain('14');
        expect(text('usage-iris-messages')).toContain('5,400');
    });

    it('hides the cards of modules that are not enabled and builds that did not run', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="usage-semester-SS 2026"]').click();
        await fixture.whenStable();

        for (const testId of ['usage-builds', 'usage-build-duration', 'usage-tutorial-groups', 'usage-iris-messages']) {
            expect(fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)).toBeNull();
        }
        expect(text('usage-posts')).toContain('0');
    });

    it('still renders a response in the shape of an older server without builds and communication', async () => {
        const { builds: _builds, communication: _communication, irisMessages: _iris, tutorialGroups: _groups, ...olderShape } = statistics.semesters[0];
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt, semesters: [olderShape] });
        await fixture.whenStable();

        expect(text('usage-courses')).toContain('12');
        for (const testId of ['usage-builds', 'usage-build-duration', 'usage-communication']) {
            expect(fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)).toBeNull();
        }
    });

    it('shows zero Iris messages but names Iris in the heading only if Iris is enabled', async () => {
        const withIris = { ...statistics.semesters[0], irisMessages: 0 };
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt, semesters: [withIris] });
        await fixture.whenStable();

        expect(text('usage-iris-messages')).toContain('0');
        expect(text('usage-communication')).toContain('artemisApp.usageDashboard.communicationAndIris');
    });

    it('names only communication in the heading if Iris is not enabled', async () => {
        const { irisMessages: _iris, ...withoutIris } = statistics.semesters[0];
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt, semesters: [withoutIris] });
        await fixture.whenStable();

        expect(text('usage-communication')).toContain('artemisApp.usageDashboard.communication');
        expect(text('usage-communication')).not.toContain('communicationAndIris');
    });

    it('shows the number of builds without an average duration if no build was successful', async () => {
        const noSuccess = { ...statistics.semesters[0], builds: { count: 7 } };
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt, semesters: [noSuccess] });
        await fixture.whenStable();

        expect(text('usage-builds')).toContain('7');
        expect(fixture.nativeElement.querySelector('[data-testid="usage-build-duration"]')).toBeNull();
    });

    it('switches the headline numbers when another semester is selected', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="usage-semester-SS 2026"]').click();
        await fixture.whenStable();

        expect(text('usage-courses')).toContain('8');
        expect(text('usage-exercises')).toContain('20');
    });

    it('shows a hint instead of numbers when no semester has enough courses', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt });
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('[data-testid="usage-empty"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="usage-cards"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="usage-loading"]')).toBeNull();
    });

    it('shows an error message when the request fails', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush('error', { status: 500, statusText: 'Server Error' });
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('[data-testid="usage-error"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="usage-cards"]')).toBeNull();
    });

    it('has the same translation keys in English and German', () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt });
        const keys = (value: object, prefix = ''): string[] =>
            Object.entries(value).flatMap(([key, child]) => (typeof child === 'object' ? keys(child, `${prefix}${key}.`) : [`${prefix}${key}`]));

        expect(keys(deUsageDashboard).sort()).toEqual(keys(enUsageDashboard).sort());
    });
});
