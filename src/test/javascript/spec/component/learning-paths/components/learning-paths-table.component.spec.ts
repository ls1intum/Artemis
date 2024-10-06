import '@angular/localize/init';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathsTableComponent } from 'app/course/learning-paths/components/learning-paths-table/learning-paths-table.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LearningPathInformationDTO } from 'app/entities/competency/learning-path.model';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { By } from '@angular/platform-browser';

describe('LearningPathsTableComponent', () => {
    let component: LearningPathsTableComponent;
    let fixture: ComponentFixture<LearningPathsTableComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let getLearningPathInformationSpy: jest.SpyInstance;

    const courseId = 1;

    const searchResults = <SearchResult<LearningPathInformationDTO>>{
        numberOfPages: 2,
        resultsOnPage: generateResults(0, 50),
    };

    const pageable = <SearchTermPageableSearch>{
        page: 1,
        pageSize: 50,
        searchTerm: '',
        sortingOrder: 'ASCENDING',
        sortedColumn: 'ID',
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathsTableComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: AlertService, useClass: MockAlertService },
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        getLearningPathInformationSpy = jest.spyOn(learningPathApiService, 'getLearningPathInformation').mockResolvedValue(searchResults);

        fixture = TestBed.createComponent(LearningPathsTableComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load learning paths', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const learningPathRows = fixture.nativeElement.querySelectorAll('tr');

        expect(getLearningPathInformationSpy).toHaveBeenCalledExactlyOnceWith(courseId, pageable);

        expect(component.learningPaths()).toEqual(searchResults.resultsOnPage);
        expect(learningPathRows).toHaveLength(searchResults.resultsOnPage.length + 1);
        expect(component.collectionSize()).toBe(searchResults.resultsOnPage.length);
    });

    it('should open competency graph modal', async () => {
        const learningPathId = 1;
        const openCompetencyGraphSpy = jest.spyOn(component, 'openCompetencyGraph');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const learningPathIdButton = fixture.debugElement.query(By.css(`#open-competency-graph-button-${learningPathId}`));
        learningPathIdButton.nativeElement.click();
        expect(openCompetencyGraphSpy).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should change page', async () => {
        const onPageChangeSpy = jest.spyOn(component, 'setPage');

        fixture.detectChanges();
        await fixture.whenStable();

        await component.setPage(2);

        expect(onPageChangeSpy).toHaveBeenLastCalledWith(2);
        expect(getLearningPathInformationSpy).toHaveBeenLastCalledWith(courseId, { ...pageable, page: 2 });
    });

    it('should search for learning paths when the search term changes', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const searchField = fixture.debugElement.query(By.css('#learning-path-search'));
        const searchPageable = { ...pageable, searchTerm: 'Search Term' };
        searchField.nativeElement.value = 'Search Term';
        searchField.nativeElement.dispatchEvent(new Event('input'));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getLearningPathInformationSpy).toHaveBeenLastCalledWith(courseId, searchPageable);
    });

    it('should show error message when loading learning paths fails', async () => {
        getLearningPathInformationSpy.mockRejectedValue(new Error('Error loading learning paths'));
        const alertServiceErrorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    function generateResults(start: number = 0, end: number): LearningPathInformationDTO[] {
        return Array.from({ length: end - start }, (_, i) => ({
            id: i + start,
            user: { name: `User ${i + start}`, login: `user${i + start}` },
            progress: i + start,
        }));
    }
});
