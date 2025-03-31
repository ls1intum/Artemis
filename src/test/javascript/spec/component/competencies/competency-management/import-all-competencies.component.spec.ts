import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { ImportAllCompetenciesComponent } from 'app/atlas/manage/competency-management/import-all-competencies.component';
import { Course } from 'app/core/shared/entities/course.model';
import { MockRouter } from '../../../helpers/mocks/mock-router';

import { NgbActiveModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ImportAllCompetenciesComponent', () => {
    let fixture: ComponentFixture<ImportAllCompetenciesComponent>;
    let component: ImportAllCompetenciesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ImportAllCompetenciesComponent, MockComponent(NgbPagination), FontAwesomeTestingModule],
            declarations: [MockRouter, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(NgbActiveModal), provideHttpClient(), provideHttpClientTesting()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ImportAllCompetenciesComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should extract competency properties', () => {
        const course: Course = {
            title: 'Course Title',
            shortName: 'CT',
            semester: 'WS24',
        };

        expect(component.columns).toBeDefined();
        expect(component.columns).toHaveLength(3);

        const titleProperty = component.columns[0];
        expect(titleProperty.getProperty(course)).toBe(course.title);

        const shortNameProperty = component.columns[1];
        expect(shortNameProperty.getProperty(course)).toBe(course.shortName);

        const semesterProperty = component.columns[2];
        expect(semesterProperty.getProperty(course)).toBe(course.semester);
    });
});
