import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { CompetencySearchComponent } from 'app/atlas/manage/import/competency-search.component';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { CourseCompetencyFilter, PageableSearch } from 'app/shared/table/pageable-table';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { getComponentInstanceFromFixture } from 'test/helpers/utils/general-test.utils';

@Component({
    template: '<jhi-competency-search [(search)]="search" (searchChange)="searchChange($event)"/>',
    imports: [CompetencySearchComponent],
})
class WrapperComponent {
    search: CourseCompetencyFilter;

    searchChange(search: PageableSearch) {}
}

describe('CompetencySearchComponent', () => {
    let fixture: ComponentFixture<WrapperComponent>;
    let component: WrapperComponent;
    let competencySearchComponent: CompetencySearchComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [WrapperComponent],
            declarations: [CompetencySearchComponent, MockPipe(ArtemisTranslatePipe), ButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(WrapperComponent);
                component = fixture.componentInstance;
                competencySearchComponent = getComponentInstanceFromFixture(fixture, CompetencySearchComponent);

                component.search = {
                    title: '',
                    semester: '',
                    courseTitle: '',
                    description: '',
                };

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeDefined();
    });

    it('should reset', () => {
        initializeSearch();

        fixture.debugElement.nativeElement.querySelector('#resetFilterButton > .jhi-btn').click();

        for (const key in component.search) {
            expect(competencySearchComponent.search()[key as keyof CourseCompetencyFilter]).toBe('');
        }
    });

    it('should submit with only title', () => {
        const searchChangeSpy = jest.spyOn(component, 'searchChange');

        initializeSearch();

        fixture.debugElement.nativeElement.querySelector('#submitFilterButton > .jhi-btn').click();
        expect(searchChangeSpy).toHaveBeenCalledWith({ title: 'any value', description: '', courseTitle: '', semester: '' });
    });

    it('should submit with advanced search', () => {
        const searchChangeSpy = jest.spyOn(component, 'searchChange');

        initializeSearch();
        competencySearchComponent.advancedSearchEnabled = true;

        fixture.debugElement.nativeElement.querySelector('#submitFilterButton > .jhi-btn').click();
        expect(searchChangeSpy).toHaveBeenCalledWith({ title: 'any value', description: 'any value', courseTitle: 'any value', semester: 'any value' });
    });

    it('should toggle advanced search', () => {
        const advancedSearchToggle = fixture.debugElement.nativeElement.querySelector('#toggleAdvancedSearch');

        advancedSearchToggle.click();
        expect(competencySearchComponent.advancedSearchEnabled).toBeTrue();

        advancedSearchToggle.click();
        expect(competencySearchComponent.advancedSearchEnabled).toBeFalse();
    });

    function initializeSearch(): void {
        for (const key in component.search) {
            component.search[key as keyof CourseCompetencyFilter] = 'any value';
        }
        fixture.detectChanges();
    }
});
