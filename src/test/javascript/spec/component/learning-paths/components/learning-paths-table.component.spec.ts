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

describe('LearningPathsTableComponent', () => {
    let component: LearningPathsTableComponent;
    let fixture: ComponentFixture<LearningPathsTableComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let getLearningPathInformationSpy: jest.SpyInstance;

    const courseId = 1;

    const searchResults = <SearchResult<LearningPathInformationDTO>>{
        numberOfPages: 1,
        resultsOnPage: [
            {
                id: 1,
                user: { name: 'User 1', login: 'user1' },
                progress: 30,
            },
            {
                id: 2,
                user: { name: 'User 2', login: 'user2' },
                progress: 50,
            },
            {
                id: 3,
                user: { name: 'User 3', login: 'user3' },
                progress: 70,
            },
            {
                id: 4,
                user: { name: 'User 4', login: 'user4' },
                progress: 100,
            },
        ],
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

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseId()).toBe(courseId);
    });

    it('should load learning paths', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getLearningPathInformationSpy).toHaveBeenCalledWith(courseId, pageable);

        expect(component.learningPaths()).toEqual(searchResults.resultsOnPage);
        expect(component.collectionSize()).toBe(searchResults.resultsOnPage.length);
    });

    it('should show error message when loading learning paths fails', async () => {
        getLearningPathInformationSpy.mockRejectedValue(new Error());
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
});
