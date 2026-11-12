import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { VcsRepositoryAccessLogViewComponent } from './vcs-repository-access-log-view.component';
import { VcsAccessLogDTO } from 'app/programming/shared/entities/vcs-access-log-entry.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { TableViewComponent } from 'app/shared-ui/table-view/table-view';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';

const translationPrefix = 'artemisApp.repository.vcsAccessLog.';

describe('VcsRepositoryAccessLogViewComponent', () => {
    let fixture: ComponentFixture<VcsRepositoryAccessLogViewComponent>;
    let params: BehaviorSubject<Params>;
    let participationLog: ReturnType<typeof vi.fn<() => Observable<VcsAccessLogDTO[] | undefined>>>;
    let repositoryLog: typeof participationLog;

    const entries: VcsAccessLogDTO[] = [
        {
            id: 1,
            userId: 98765,
            name: 'Alice',
            email: 'alice@example.org',
            commitHash: 'abcde',
            authenticationMechanism: 'SSH',
            repositoryActionType: 'PUSH',
            timestamp: dayjs('2021-01-02T10:00:00Z'),
        },
        {
            id: 2,
            userId: 8,
            name: 'Bob',
            email: 'bob@example.org',
            commitHash: 'fffee',
            authenticationMechanism: 'PASSWORD',
            repositoryActionType: 'PULL',
            timestamp: dayjs('2021-01-03T10:00:00Z'),
        },
    ];

    function createComponent() {
        fixture = TestBed.createComponent(VcsRepositoryAccessLogViewComponent);
        fixture.detectChanges();
    }

    function cells(field: string): HTMLElement[] {
        return Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="table-cell"][data-column="${field}"]`));
    }

    function table(): TableViewComponent<unknown> {
        const element = fixture.debugElement.query(By.directive(TableViewComponent));
        expect(element).toBeTruthy();
        return element.componentInstance;
    }

    beforeEach(async () => {
        params = new BehaviorSubject<Params>({ repositoryId: '5', repositoryType: 'USER' });
        participationLog = vi.fn(() => of(entries));
        repositoryLog = vi.fn(() => of(entries));
        await TestBed.configureTestingModule({
            imports: [VcsRepositoryAccessLogViewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { params } },
                {
                    provide: ProgrammingExerciseParticipationService,
                    useValue: { getVcsAccessLogForParticipation: participationLog, getVcsAccessLogForRepository: repositoryLog },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        }).compileComponents();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    it('loads participation logs and registers the title in the course top bar', () => {
        createComponent();
        expect(participationLog).toHaveBeenCalledExactlyOnceWith(5);
        expect(repositoryLog).not.toHaveBeenCalled();
        expect(TestBed.inject(CourseTitleBarService).titleTemplate()).toBeDefined();
        expect(fixture.nativeElement.querySelector('h3')).toBeNull();
        fixture.destroy();
        expect(TestBed.inject(CourseTitleBarService).titleTemplate()).toBeUndefined();
    });

    it.each(['TEMPLATE', 'SOLUTION', 'TESTS'])('loads %s repository logs', (repositoryType) => {
        params.next({ exerciseId: '10', repositoryType });
        createComponent();
        expect(repositoryLog).toHaveBeenCalledExactlyOnceWith(10, repositoryType);
        expect(participationLog).not.toHaveBeenCalled();
    });

    it('uses the shared compact scrolling table and sorts newest first', () => {
        createComponent();
        expect(table().options()).toMatchObject({ lazy: false, fill: true, initialSortField: 'timestamp', initialSortOrder: -1 });
        expect(fixture.nativeElement.querySelector('table[tumAetUiTable]')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('tumaet-ui-paginator')).toBeTruthy();
        expect(cells('author').map((cell) => cell.textContent?.trim())).toEqual(['Bob, bob@example.org', 'Alice, alice@example.org']);
        expect(fixture.nativeElement.querySelector('th[aria-sort="descending"]')?.textContent).toContain(`${translationPrefix}timeStamp`);
    });

    it('offers one global search without per-column search controls', () => {
        createComponent();
        expect(fixture.nativeElement.querySelectorAll('input[type="search"]')).toHaveLength(1);
        expect(fixture.nativeElement.querySelectorAll('thead input')).toHaveLength(0);
    });

    it('sorts authors and timestamps chronologically across timezone offsets', () => {
        participationLog.mockReturnValue(
            of([
                { ...entries[0], timestamp: dayjs('2021-01-02T23:30:00-02:00') },
                { ...entries[1], timestamp: dayjs('2021-01-03T00:30:00+02:00') },
            ]),
        );
        createComponent();
        expect(cells('author')[0].textContent).toContain('Alice');
        const element: HTMLElement = fixture.nativeElement;
        const headers = Array.from(element.querySelectorAll<HTMLTableCellElement>('th'));
        headers
            .find((header) => header.textContent?.includes(`${translationPrefix}timeStamp`))!
            .querySelector('button')!
            .click();
        fixture.detectChanges();
        expect(cells('author')[0].textContent).toContain('Bob');
        headers
            .find((header) => header.textContent?.includes(`${translationPrefix}author`))!
            .querySelector('button')!
            .click();
        fixture.detectChanges();
        expect(cells('author')[0].textContent).toContain('Alice');
    });

    it('handles timestamps returned as ISO strings by the API', () => {
        participationLog.mockReturnValue(of(JSON.parse(JSON.stringify(entries))));
        createComponent();
        expect(cells('author')[0].textContent).toContain('Bob');
        expect(cells('timestamp')[0].textContent).toContain('2021');
    });

    it('searches fixed-offset Dayjs timestamps by the displayed local date', async () => {
        vi.useFakeTimers();
        const timestamp = dayjs('2021-01-03T10:00:00Z').utcOffset(-12 * 60);
        participationLog.mockReturnValue(of([{ ...entries[0], timestamp }]));
        createComponent();
        table().onGlobalSearch(dayjs(timestamp.valueOf()).format('YYYY-MM-DD'));
        await vi.advanceTimersByTimeAsync(300);
        fixture.detectChanges();
        expect(cells('author')).toHaveLength(1);
    });

    it('searches emails and commit hashes and resets pagination with filtered totals', async () => {
        vi.useFakeTimers();
        participationLog.mockReturnValue(of(Array.from({ length: 60 }, (_, index) => ({ ...entries[0], id: index, commitHash: `commit-${index}` }))));
        createComponent();
        table().pageChange({ first: 50, rows: 50 });
        fixture.detectChanges();
        expect(cells('author')).toHaveLength(10);
        table().onGlobalSearch('  COMMIT-59  ');
        await vi.advanceTimersByTimeAsync(300);
        fixture.detectChanges();
        expect(cells('commitHash').map((cell) => cell.textContent?.trim())).toEqual(['commit-59']);
        expect(table()['effectiveTotalRows']()).toBe(1);
        table().onGlobalSearch('alice@example.org');
        await vi.advanceTimersByTimeAsync(300);
        fixture.detectChanges();
        expect(cells('author')).toHaveLength(50);
        expect(table()['effectiveTotalRows']()).toBe(60);
    });

    it.each([
        ['PUSH', 'Alice'],
        ['PASSWORD', 'Bob'],
        ['98765', 'Alice'],
    ])('finds %s through global search and shows an empty state for no matches', async (query, author) => {
        vi.useFakeTimers();
        createComponent();
        table().onGlobalSearch(query);
        await vi.advanceTimersByTimeAsync(300);
        fixture.detectChanges();
        expect(cells('author')).toHaveLength(1);
        expect(cells('author')[0].textContent).toContain(author);
        table().onGlobalSearch('no matching access');
        await vi.advanceTimersByTimeAsync(300);
        fixture.detectChanges();
        expect(cells('author')).toHaveLength(0);
        expect(fixture.nativeElement.textContent).toContain(`${translationPrefix}empty`);
    });

    it('renders and searches a build agent without an email or user ID', async () => {
        vi.useFakeTimers();
        participationLog.mockReturnValue(of([{ ...entries[0], userId: undefined, name: 'Build agent artemis-build-agent-1 (build job 42)', email: undefined }]));
        createComponent();
        expect(cells('author')[0].textContent).toContain('artemis-build-agent-1');
        expect(fixture.nativeElement.textContent).not.toContain('undefined');
        table().onGlobalSearch('build job 42');
        await vi.advanceTimersByTimeAsync(300);
        fixture.detectChanges();
        expect(cells('author')).toHaveLength(1);
    });

    it('shows loading, clears stale rows and cancels the previous request when the repository changes', () => {
        createComponent();
        const pending = new Subject<VcsAccessLogDTO[]>();
        const canceled = vi.fn();
        repositoryLog.mockReturnValue(
            new Observable((subscriber) => {
                const subscription = pending.subscribe(subscriber);
                return () => {
                    canceled();
                    subscription.unsubscribe();
                };
            }),
        );
        params.next({ exerciseId: '10', repositoryType: 'TEMPLATE' });
        fixture.detectChanges();
        expect(table().loading()).toBe(true);
        expect(cells('author')).toHaveLength(0);
        params.next({ repositoryId: '6', repositoryType: 'USER' });
        fixture.detectChanges();
        expect(canceled).toHaveBeenCalledOnce();
        expect(table().loading()).toBe(false);
        expect(cells('author')).toHaveLength(2);
        pending.next([{ ...entries[0], name: 'Stale author' }]);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).not.toContain('Stale author');
    });

    it('clears previous rows on errors and can load another repository afterwards', () => {
        createComponent();
        const alert = vi.spyOn(TestBed.inject(AlertService), 'error');
        repositoryLog.mockReturnValue(throwError(() => new Error('Unavailable')));
        params.next({ exerciseId: '10', repositoryType: 'TEMPLATE' });
        fixture.detectChanges();
        expect(alert).toHaveBeenCalledWith(`${translationPrefix}error`);
        expect(table().loading()).toBe(false);
        expect(cells('author')).toHaveLength(0);
        params.next({ repositoryId: '6', repositoryType: 'USER' });
        fixture.detectChanges();
        expect(cells('author')).toHaveLength(2);
    });

    it('handles an empty response', () => {
        participationLog.mockReturnValue(of(undefined));
        createComponent();
        expect(table().loading()).toBe(false);
        expect(cells('author')).toHaveLength(0);
        expect(fixture.nativeElement.textContent).toContain(`${translationPrefix}empty`);
    });
});
