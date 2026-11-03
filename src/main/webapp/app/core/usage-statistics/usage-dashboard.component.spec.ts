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
                enrollments: 5100,
                instructors: 21,
                tutors: 130,
                activeStudents: 2000,
                submissions: 150000,
                lectures: 80,
                lectureUnits: 640,
                exercisesByType: { programming: 40, quiz: 10, text: 5, modeling: 3, 'file-upload': 2 },
                builds: { count: 25000, averageDurationSeconds: 41.6 },
                communication: { posts: 900, replies: 1800, reactions: 3200, faqs: 14 },
                irisMessages: 5400,
                tutorialGroups: 36,
                exams: 28,
                examRegistrations: 9600,
                examParticipations: 8900,
                competencies: 410,
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

    it('groups the numbers into courses, exercises and exams', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        const inSection = (section: string, testId: string) => fixture.nativeElement.querySelector(`[data-testid="${section}"] [data-testid="${testId}"]`) !== null;
        for (const testId of ['usage-courses', 'usage-students', 'usage-enrollments', 'usage-lectures', 'usage-competencies', 'usage-tutorial-groups']) {
            expect(inSection('usage-courses-section', testId), testId).toBe(true);
        }
        for (const testId of ['usage-exercises', 'usage-active-students', 'usage-submissions', 'usage-builds', 'usage-build-duration']) {
            expect(inSection('usage-exercises-section', testId), testId).toBe(true);
        }
        for (const testId of ['usage-exams', 'usage-exam-registrations']) {
            expect(inSection('usage-exams-section', testId), testId).toBe(true);
        }
    });

    it('shows the non-unique enrollments and exam registrations next to the unique students', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        expect(text('usage-students')).toContain('3,400');
        expect(text('usage-enrollments')).toContain('5,100');
        expect(text('usage-exam-registrations')).toContain('9,600');
    });

    it('explains every number in a tooltip button next to its heading instead of a block of definitions', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        const cards: Record<string, string> = {
            'usage-courses': 'courses',
            'usage-students': 'students',
            'usage-enrollments': 'enrollments',
            'usage-instructors': 'instructors',
            'usage-tutors': 'tutors',
            'usage-lectures': 'lectures',
            'usage-lecture-units': 'lectureUnits',
            'usage-competencies': 'competencies',
            'usage-tutorial-groups': 'tutorialGroups',
            'usage-exercises': 'exercises',
            'usage-active-students': 'activeStudents',
            'usage-submissions': 'submissions',
            'usage-builds': 'builds',
            'usage-build-duration': 'averageBuildDuration',
            'usage-exams': 'exams',
            'usage-exam-registrations': 'examRegistrations',
            'usage-exam-participations': 'examParticipations',
            'usage-posts': 'posts',
            'usage-replies': 'replies',
            'usage-reactions': 'reactions',
            'usage-faqs': 'faqs',
            'usage-iris-messages': 'irisMessages',
        };
        for (const [card, key] of Object.entries(cards)) {
            const hint = fixture.nativeElement.querySelector(`[data-testid="${card}"] [data-testid="usage-hint-${key}"]`);
            expect(hint, card).not.toBeNull();
            expect(hint.getAttribute('aria-label'), card).toBeTruthy();
            expect(enUsageDashboard.artemisApp.usageDashboard.hints[key as keyof typeof enUsageDashboard.artemisApp.usageDashboard.hints], key).toBeTruthy();
        }
        expect(fixture.nativeElement.querySelector('[data-testid="usage-communication"] [data-testid="usage-hint-communication"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="usage-definitions"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="usage-footer"]')).not.toBeNull();
    });

    it('shows the staff, the lecture units and the exam participations next to the related numbers', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        expect(text('usage-instructors')).toContain('21');
        expect(text('usage-tutors')).toContain('130');
        expect(text('usage-lecture-units')).toContain('640');
        expect(text('usage-exam-participations')).toContain('8,900');
        expect(fixture.nativeElement.querySelector('[data-testid="usage-courses-section"] [data-testid="usage-instructors"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="usage-courses-section"] [data-testid="usage-lecture-units"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="usage-exams-section"] [data-testid="usage-exam-participations"]')).not.toBeNull();
    });

    it('hides the new numbers in the response of a server that predates them', async () => {
        const { instructors, tutors, lectureUnits, examParticipations, ...older } = statistics.semesters[0];
        httpMock.expectOne('api/core/public/usage-statistics').flush({ ...statistics, semesters: [older] });
        await fixture.whenStable();

        for (const testId of ['usage-instructors', 'usage-tutors', 'usage-lecture-units', 'usage-exam-participations']) {
            expect(fixture.nativeElement.querySelector(`[data-testid="${testId}"]`), testId).toBeNull();
        }
        expect(text('usage-lectures')).toContain('80');
        expect(instructors + tutors + lectureUnits + examParticipations).toBeGreaterThan(0);
    });

    it('hides the whole exam section if exams are not enabled', async () => {
        const { exams: _exams, examRegistrations: _registrations, ...withoutExams } = statistics.semesters[0];
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt, semesters: [withoutExams] });
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('[data-testid="usage-exams-section"]')).toBeNull();
        expect(text('usage-enrollments')).toContain('5,100');
    });

    it('shows builds, tutorial groups, communication and Iris messages', async () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush(statistics);
        await fixture.whenStable();

        expect(text('usage-builds')).toContain('25,000');
        expect(text('usage-build-duration')).toContain('42 s');
        expect(text('usage-tutorial-groups')).toContain('36');
        expect(text('usage-exams')).toContain('28');
        expect(text('usage-competencies')).toContain('410');
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

        for (const testId of [
            'usage-builds',
            'usage-build-duration',
            'usage-tutorial-groups',
            'usage-exams',
            'usage-exam-registrations',
            'usage-competencies',
            'usage-iris-messages',
        ]) {
            expect(fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)).toBeNull();
        }
        expect(text('usage-posts')).toContain('0');
    });

    it('still renders a response in the shape of an older server without builds and communication', async () => {
        const {
            builds: _builds,
            communication: _communication,
            irisMessages: _iris,
            tutorialGroups: _groups,
            exams: _exams,
            examRegistrations: _registrations,
            enrollments: _enrollments,
            competencies: _competencies,
            ...olderShape
        } = statistics.semesters[0];
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt, semesters: [olderShape] });
        await fixture.whenStable();

        expect(text('usage-courses')).toContain('12');
        for (const testId of [
            'usage-builds',
            'usage-build-duration',
            'usage-communication',
            'usage-exams',
            'usage-exam-registrations',
            'usage-enrollments',
            'usage-competencies',
        ]) {
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

    it('hides the exam card if the exam module is not enabled and still shows the competencies', async () => {
        const { exams: _exams, ...withoutExams } = statistics.semesters[0];
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt, semesters: [withoutExams] });
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('[data-testid="usage-exams"]')).toBeNull();
        expect(text('usage-competencies')).toContain('410');
    });

    it('shows zero exams if the exam module is enabled but no real exam exists', async () => {
        const { competencies: _competencies, ...withoutCompetencies } = { ...statistics.semesters[0], exams: 0 };
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt, semesters: [withoutCompetencies] });
        await fixture.whenStable();

        expect(text('usage-exams')).toContain('0');
        expect(fixture.nativeElement.querySelector('[data-testid="usage-competencies"]')).toBeNull();
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

    it('uses the placeholder syntax of the translator in the accessible name of the hint buttons', () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt });
        // the translator replaces {{ name }}, a single brace would stay literal and every button would have the same name
        for (const translations of [enUsageDashboard, deUsageDashboard]) {
            expect(translations.artemisApp.usageDashboard.hintsLabel).toContain('{{ name }}');
            expect(translations.artemisApp.usageDashboard.hintsLabel).not.toMatch(/(?<!\{)\{name\}(?!\})/);
        }
    });

    it('keeps the caveat of the builds in the tooltip, because the cards vanish for semesters older than the build job retention', () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt });
        expect(enUsageDashboard.artemisApp.usageDashboard.hints.builds).toContain('only shown for semesters that started within that period');
        expect(deUsageDashboard.artemisApp.usageDashboard.hints.builds).toContain('nur für Semester angezeigt, die innerhalb dieser Zeit begonnen haben');
    });

    it('has the same translation keys in English and German', () => {
        httpMock.expectOne('api/core/public/usage-statistics').flush({ generatedAt: statistics.generatedAt });
        const keys = (value: object, prefix = ''): string[] =>
            Object.entries(value).flatMap(([key, child]) => (typeof child === 'object' ? keys(child, `${prefix}${key}.`) : [`${prefix}${key}`]));

        expect(keys(deUsageDashboard).sort()).toEqual(keys(enUsageDashboard).sort());
    });
});
