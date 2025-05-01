import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { CompetencySearchComponent } from 'app/atlas/manage/import/competency-search.component';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { CourseCompetencyFilter } from 'app/shared/table/pageable-table';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('CompetencySearchComponent', () => {
    let componentFixture: ComponentFixture<CompetencySearchComponent>;
    let component: CompetencySearchComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CompetencySearchComponent, MockPipe(ArtemisTranslatePipe), ButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CompetencySearchComponent);
                component = componentFixture.componentInstance;
                component.search = {
                    title: '',
                    semester: '',
                    courseTitle: '',
                    description: '',
                };
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should reset', () => {
        componentFixture.detectChanges();
        initializeSearch();

        componentFixture.debugElement.nativeElement.querySelector('#resetFilterButton > .jhi-btn').click();

        for (const key in component.search) {
            expect(component.search[key as keyof CourseCompetencyFilter]).toBe('');
        }
    });

    it('should submit with only title', () => {
        componentFixture.detectChanges();
        const searchChangeEmitSpy = jest.spyOn(component.searchChange, 'emit');

        initializeSearch();

        componentFixture.debugElement.nativeElement.querySelector('#submitFilterButton > .jhi-btn').click();
        expect(searchChangeEmitSpy).toHaveBeenCalledWith({ title: 'any value', description: '', courseTitle: '', semester: '' });
    });

    it('should submit with advanced search', () => {
        componentFixture.detectChanges();
        const searchChangeEmitSpy = jest.spyOn(component.searchChange, 'emit');

        initializeSearch();
        component.advancedSearchEnabled = true;

        componentFixture.debugElement.nativeElement.querySelector('#submitFilterButton > .jhi-btn').click();
        expect(searchChangeEmitSpy).toHaveBeenCalledWith({ title: 'any value', description: 'any value', courseTitle: 'any value', semester: 'any value' });
    });

    it('should toggle advanced search', () => {
        componentFixture.detectChanges();
        const advancedSearchToggle = componentFixture.debugElement.nativeElement.querySelector('#toggleAdvancedSearch');

        advancedSearchToggle.click();
        expect(component.advancedSearchEnabled).toBeTrue();

        advancedSearchToggle.click();
        expect(component.advancedSearchEnabled).toBeFalse();
    });

    function initializeSearch(): void {
        for (const key in component.search) {
            component.search[key as keyof CourseCompetencyFilter] = 'any value';
        }
    }
});
