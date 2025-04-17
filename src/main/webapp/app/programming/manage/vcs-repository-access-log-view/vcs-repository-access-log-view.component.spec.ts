import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { MockDirective } from 'ng-mocks';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import '@angular/localize/init';
import { VcsRepositoryAccessLogViewComponent } from 'app/programming/manage/vcs-repository-access-log-view/vcs-repository-access-log-view.component';
import { VcsAccessLogDTO } from 'app/programming/shared/entities/vcs-access-log-entry.model';
import { AlertService } from 'app/shared/service/alert.service';
import { VcsRepositoryAccessLogService } from '../../shared/services/vcs-repository-access-log.service';
import { SearchResult, SortingOrder } from '../../../shared/table/pageable-table';
import { TranslateDirective } from '../../../shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';

describe('VcsRepositoryAccessLogViewComponent', () => {
    let fixture: ComponentFixture<VcsRepositoryAccessLogViewComponent>;
    let component: VcsRepositoryAccessLogViewComponent;
    let accessLogService: VcsRepositoryAccessLogService;
    let alertService: AlertService;
    let searchSpy: jest.SpyInstance;

    const userId = 4;

    const mockVcsAccessLog: VcsAccessLogDTO[] = [
        {
            id: 1,
            userId: userId,
            name: 'authorName',
            email: 'authorEmail',
            commitHash: 'abcde',
            authenticationMechanism: 'SSH',
            repositoryActionType: 'WRITE',
            timestamp: dayjs('2021-01-02'),
        },
        {
            id: 2,
            userId: userId,
            name: 'authorName',
            email: 'authorEmail',
            commitHash: 'fffee',
            authenticationMechanism: 'SSH',
            repositoryActionType: 'READ',
            timestamp: dayjs('2021-01-03'),
        },
    ];

    const route = { params: of({ repositoryId: '5', repositoryType: 'USER', exerciseId: 4 }) } as any as ActivatedRoute;

    function setupTestBed() {
        fixture = TestBed.createComponent(VcsRepositoryAccessLogViewComponent);
        component = fixture.componentInstance;
        accessLogService = TestBed.inject(VcsRepositoryAccessLogService);
        alertService = TestBed.inject(AlertService);

        searchSpy = jest.spyOn(accessLogService, 'search').mockResolvedValue({
            resultsOnPage: mockVcsAccessLog,
            numberOfPages: 1,
        } as SearchResult<VcsAccessLogDTO>);
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [VcsRepositoryAccessLogViewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: AlertService, useClass: MockAlertService },
                {
                    provide: VcsRepositoryAccessLogService,
                    useValue: { search: jest.fn() },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                MockDirective(TranslateDirective),
            ],
        }).compileComponents();
    });

    it('should create the component', () => {
        setupTestBed();
        expect(component).toBeTruthy();
    });

    it('should load participation vcs access log', async () => {
        setupTestBed();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(searchSpy).toHaveBeenCalledOnce();
        expect(component.content().resultsOnPage).toEqual(mockVcsAccessLog);
    });

    it('should load template repository vcs access log', async () => {
        route.params = of({ exerciseId: '10', repositoryType: 'TEMPLATE' });
        TestBed.overrideProvider(ActivatedRoute, { useValue: route });

        setupTestBed();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(searchSpy).toHaveBeenCalledOnce();
        expect(component.content().resultsOnPage).toEqual(mockVcsAccessLog);
    });

    it('should display an alert when fetching logs fails', async () => {
        setupTestBed();
        jest.spyOn(accessLogService, 'search').mockRejectedValue(new Error('error'));
        const alertSpy = jest.spyOn(alertService, 'error');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.repository.vcsAccessLog.error');
        expect(component.content().resultsOnPage).toEqual([]);
    });
    it('should correctly render log data in table', async () => {
        setupTestBed();
        fixture.detectChanges();
        await fixture.whenStable(); // Wait for async changes
        fixture.detectChanges();

        const tableRows = fixture.nativeElement.querySelectorAll('tbody tr');

        // Ensure the number of rows matches the expected data
        expect(component.content().resultsOnPage).toEqual(mockVcsAccessLog);
        expect(component.content().resultsOnPage).toHaveLength(2);
        expect(tableRows).toHaveLength(mockVcsAccessLog.length);

        mockVcsAccessLog.forEach((log, index) => {
            const columns = tableRows[index].querySelectorAll('td');

            expect(columns[0].textContent.replace(/\s+/g, ' ').trim()).toBe(log.id?.toString());
            expect(columns[1].textContent.replace(/\s+/g, ' ').trim()).toBe(log.userId?.toString());
            expect(columns[2].textContent.replace(/\s+/g, ' ').trim()).toBe(`${log.name}, ${log.email}`);
            expect(columns[3].textContent.replace(/\s+/g, ' ').trim()).toBe(log.repositoryActionType);
            expect(columns[4].textContent.replace(/\s+/g, ' ').trim()).toBe(log.authenticationMechanism);
            expect(columns[5].textContent.replace(/\s+/g, ' ').trim()).toBe(log.commitHash);
        });
    });

    it('should correctly sort logs when clicking on column header', () => {
        setupTestBed();
        fixture.detectChanges();

        const setSortedColumnSpy = jest.spyOn(component, 'setSortedColumn');

        // Simulate clicking on a sortable header
        const column = 'timestamp';
        component.setSortedColumn(column);

        fixture.detectChanges();

        expect(setSortedColumnSpy).toHaveBeenCalledWith(column);
        expect(component.getSortDirection(column)).toBe('ASCENDING');
    });

    it('should display correct pagination text for one page', async () => {
        setupTestBed();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const paginationText = fixture.nativeElement.querySelector('.text-muted.text-end').textContent.trim();
        expect(paginationText).toContain('artemisApp.repository.vcsAccessLog.onePage');
    });

    it('should toggle sorting order when the same column is clicked', () => {
        setupTestBed();
        fixture.detectChanges();

        const columnName = 'userId';
        component.sortedColumn.set(columnName);
        component.sortingOrder.set(SortingOrder.ASCENDING);
        component.setSortedColumn(columnName);

        expect(component.sortingOrder()).toBe(SortingOrder.DESCENDING);

        component.setSortedColumn(columnName);

        expect(component.sortingOrder()).toBe(SortingOrder.ASCENDING);
    });

    it('should display correct pagination text for multiple pages', async () => {
        setupTestBed();
        jest.spyOn(accessLogService, 'search').mockResolvedValue({
            resultsOnPage: mockVcsAccessLog,
            numberOfPages: 3,
        } as SearchResult<VcsAccessLogDTO>);

        fixture.detectChanges();
        await fixture.whenStable();

        const paginationText = fixture.nativeElement.querySelector('.text-muted.text-end').textContent.trim();
        expect(paginationText).toContain('artemisApp.repository.vcsAccessLog.numberOfPages');
    });
});
