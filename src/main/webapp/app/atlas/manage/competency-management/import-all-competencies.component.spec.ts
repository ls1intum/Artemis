import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ImportAllCompetenciesComponent } from 'app/atlas/manage/competency-management/import-all-competencies.component';
import { Course } from 'app/course/shared/entities/course.model';

import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
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
    let dialogConfig: DynamicDialogConfig;

    beforeEach(async () => {
        dialogRef = {
            close: vi.fn(),
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;
        dialogConfig = { data: {} } as DynamicDialogConfig;

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
                { provide: DynamicDialogConfig, useValue: dialogConfig },
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

    it('keeps disabledIds provided via dialog data (base effect must not clobber it)', () => {
        // Regression guard: when opened via DialogService, disabledIds arrives through DynamicDialogConfig.data and
        // is applied in ngOnInit. The base ImportComponent effect must NOT reset it to the input default [] —
        // otherwise the same-course import-exclusion guard silently breaks.
        dialogConfig.data = { disabledIds: [42] };
        fixture.detectChanges();
        expect(component.disabledIds).toContain(42);
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
