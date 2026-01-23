import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ImportAllCompetenciesComponent } from 'app/atlas/manage/competency-management/import-all-competencies.component';
import { Course } from 'app/core/course/shared/entities/course.model';

import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ImportAllCompetenciesComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ImportAllCompetenciesComponent>;
    let component: ImportAllCompetenciesComponent;
    let dialogRef: DynamicDialogRef;

    beforeEach(async () => {
        dialogRef = {
            close: vi.fn(),
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [
                ImportAllCompetenciesComponent,
                MockComponent(NgbPagination),
                FontAwesomeTestingModule,
                MockComponent(ButtonComponent),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
            ],
            declarations: [],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DynamicDialogRef, useValue: dialogRef },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ImportAllCompetenciesComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
