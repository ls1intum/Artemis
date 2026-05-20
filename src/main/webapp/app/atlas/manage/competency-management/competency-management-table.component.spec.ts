import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import '@angular/localize/init';
import { CompetencyManagementTableComponent } from 'app/atlas/manage/competency-management/competency-management-table.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import {
    CompetencyRelationType,
    CompetencyTaxonomy,
    CompetencyWithTailRelationDTO,
    CourseCompetency,
    CourseCompetencyProgress,
    CourseCompetencyType,
} from 'app/atlas/shared/entities/competency.model';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { Component as NgComponent } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

@NgComponent({
    template: `
        <jhi-competency-management-table
            [courseId]="1"
            [courseCompetencies]="courseCompetencies"
            [competencyType]="competencyType"
            [standardizedCompetenciesEnabled]="true"
            (competencyDeleted)="competencyDeleted($event)"
            (competenciesAdded)="competenciesAdded($event)"
        />
    `,
    imports: [CompetencyManagementTableComponent],
})
class WrappedComponent {
    protected readonly CourseCompetencyType = CourseCompetencyType;
    courseCompetencies: CourseCompetency[] = [];
    allCompetencies: CourseCompetency[] = [];
    competencyType = CourseCompetencyType.COMPETENCY;

    competencyDeleted(competencyId: number) {}
    competenciesAdded(added: CourseCompetency[]) {
        this.allCompetencies = this.allCompetencies.concat(added);
    }
}

describe('CompetencyManagementTableComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<WrappedComponent>;
    let component: WrappedComponent;
    let competencyManagementTableComponent: CompetencyManagementTableComponent;

    let competencyService: CompetencyService;
    let prerequisiteService: PrerequisiteService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [WrappedComponent],
            providers: [
                MockProvider(AlertService),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: DialogService, useClass: MockDialogService },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(WrappedComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();

                const debugEl = fixture.debugElement.query(By.directive(CompetencyManagementTableComponent));
                competencyManagementTableComponent = debugEl.componentInstance as CompetencyManagementTableComponent;

                competencyService = TestBed.inject(CompetencyService);
                prerequisiteService = TestBed.inject(PrerequisiteService);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize values', () => {
        component.competencyType = CourseCompetencyType.COMPETENCY;
        fixture.changeDetectorRef.detectChanges();
        expect(competencyManagementTableComponent.service).toEqual(competencyService);

        component.competencyType = CourseCompetencyType.PREREQUISITE;
        fixture.changeDetectorRef.detectChanges();
        expect(competencyManagementTableComponent.service).toEqual(prerequisiteService);
    });

    it('should handle import all data', () => {
        const responseBody: CompetencyWithTailRelationDTO[] = [
            { competency: { id: 1 }, tailRelations: [] },
            { competency: { id: 2 }, tailRelations: [{ id: 3, tailCompetencyId: 2, headCompetencyId: 1, relationType: CompetencyRelationType.ASSUMES }] },
        ];

        competencyManagementTableComponent.updateDataAfterImportAll(responseBody);
        expect(component.allCompetencies).toHaveLength(2);
    });

    it('should handle delete competency', () => {
        const deleteSpy = vi.spyOn(competencyService, 'delete').mockReturnValue(of(new HttpResponse<object>({ status: 200 })));
        const competencyDeletedSpy = vi.spyOn(component, 'competencyDeleted');
        const competency1 = { id: 1, type: CourseCompetencyType.COMPETENCY } as CourseCompetency;
        const competency2 = { id: 2, type: CourseCompetencyType.COMPETENCY } as CourseCompetency;
        competencyManagementTableComponent.service = competencyService;
        component.courseCompetencies = [competency1, competency2];
        fixture.changeDetectorRef.detectChanges();

        competencyManagementTableComponent.deleteCompetency(1);
        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(competencyDeletedSpy).toHaveBeenCalledWith(1);
    });

    describe('filtering', () => {
        it('should filter competencies by title (case-insensitive)', () => {
            const competency1 = { id: 1, title: 'Algebra' } as CourseCompetency;
            const competency2 = { id: 2, title: 'Biology' } as CourseCompetency;
            const competency3 = { id: 3, title: 'Analysis' } as CourseCompetency;
            component.courseCompetencies = [competency1, competency2, competency3];
            fixture.changeDetectorRef.detectChanges();

            competencyManagementTableComponent.filterText.set('al');

            const result = competencyManagementTableComponent.filteredAndSortedCompetencies();
            expect(result).toHaveLength(2);
            expect(result.map((c) => c.id)).toContain(1);
            expect(result.map((c) => c.id)).toContain(3);
        });

        it('should return empty list when filter matches nothing', () => {
            const competency1 = { id: 1, title: 'Algebra' } as CourseCompetency;
            component.courseCompetencies = [competency1];
            fixture.changeDetectorRef.detectChanges();

            competencyManagementTableComponent.filterText.set('zzz');

            expect(competencyManagementTableComponent.filteredAndSortedCompetencies()).toHaveLength(0);
        });

        it('should show all competencies when filter is cleared', () => {
            const competency1 = { id: 1, title: 'Algebra' } as CourseCompetency;
            const competency2 = { id: 2, title: 'Biology' } as CourseCompetency;
            component.courseCompetencies = [competency1, competency2];
            fixture.changeDetectorRef.detectChanges();

            competencyManagementTableComponent.filterText.set('Algebra');
            expect(competencyManagementTableComponent.filteredAndSortedCompetencies()).toHaveLength(1);

            competencyManagementTableComponent.filterText.set('');
            expect(competencyManagementTableComponent.filteredAndSortedCompetencies()).toHaveLength(2);
        });
    });

    describe('sorting', () => {
        it('should sort by title ascending by default', () => {
            const competency1 = { id: 1, title: 'Zoology' } as CourseCompetency;
            const competency2 = { id: 2, title: 'Algebra' } as CourseCompetency;
            const competency3 = { id: 3, title: 'Biology' } as CourseCompetency;
            component.courseCompetencies = [competency1, competency2, competency3];
            fixture.changeDetectorRef.detectChanges();

            const result = competencyManagementTableComponent.filteredAndSortedCompetencies();
            expect(result.map((c) => c.title)).toEqual(['Algebra', 'Biology', 'Zoology']);
        });

        it('should sort by title descending', () => {
            const competency1 = { id: 1, title: 'Zoology' } as CourseCompetency;
            const competency2 = { id: 2, title: 'Algebra' } as CourseCompetency;
            const competency3 = { id: 3, title: 'Biology' } as CourseCompetency;
            component.courseCompetencies = [competency1, competency2, competency3];
            fixture.changeDetectorRef.detectChanges();

            competencyManagementTableComponent.sortAscending = false;

            const result = competencyManagementTableComponent.filteredAndSortedCompetencies();
            expect(result.map((c) => c.title)).toEqual(['Zoology', 'Biology', 'Algebra']);
        });

        it('should sort by taxonomy ascending', () => {
            const competency1 = { id: 1, title: 'A', taxonomy: CompetencyTaxonomy.REMEMBER } as CourseCompetency;
            const competency2 = { id: 2, title: 'B', taxonomy: CompetencyTaxonomy.ANALYZE } as CourseCompetency;
            const competency3 = { id: 3, title: 'C', taxonomy: CompetencyTaxonomy.APPLY } as CourseCompetency;
            component.courseCompetencies = [competency1, competency2, competency3];
            fixture.changeDetectorRef.detectChanges();

            competencyManagementTableComponent.sortPredicate = 'taxonomy';

            const result = competencyManagementTableComponent.filteredAndSortedCompetencies();
            expect(result.map((c) => c.taxonomy)).toEqual([CompetencyTaxonomy.ANALYZE, CompetencyTaxonomy.APPLY, CompetencyTaxonomy.REMEMBER]);
        });

        it('should sort by softDueDate ascending', () => {
            const competency1 = { id: 1, title: 'A', softDueDate: dayjs('2025-06-01') } as CourseCompetency;
            const competency2 = { id: 2, title: 'B', softDueDate: dayjs('2025-01-01') } as CourseCompetency;
            const competency3 = { id: 3, title: 'C', softDueDate: dayjs('2025-03-15') } as CourseCompetency;
            component.courseCompetencies = [competency1, competency2, competency3];
            fixture.changeDetectorRef.detectChanges();

            competencyManagementTableComponent.sortPredicate = 'softDueDate';

            const result = competencyManagementTableComponent.filteredAndSortedCompetencies();
            expect(result.map((c) => c.id)).toEqual([2, 3, 1]);
        });

        it('should sort by masteredStudents ratio ascending', () => {
            const progress1 = { numberOfStudents: 10, numberOfMasteredStudents: 8 } as CourseCompetencyProgress;
            const progress2 = { numberOfStudents: 10, numberOfMasteredStudents: 2 } as CourseCompetencyProgress;
            const progress3 = { numberOfStudents: 10, numberOfMasteredStudents: 5 } as CourseCompetencyProgress;
            const competency1 = { id: 1, title: 'A', courseProgress: progress1 } as CourseCompetency;
            const competency2 = { id: 2, title: 'B', courseProgress: progress2 } as CourseCompetency;
            const competency3 = { id: 3, title: 'C', courseProgress: progress3 } as CourseCompetency;
            component.courseCompetencies = [competency1, competency2, competency3];
            fixture.changeDetectorRef.detectChanges();

            competencyManagementTableComponent.sortPredicate = 'masteredStudents';

            const result = competencyManagementTableComponent.filteredAndSortedCompetencies();
            expect(result.map((c) => c.id)).toEqual([2, 3, 1]);
        });

        it('should treat competency without progress as 0% for masteredStudents sort', () => {
            const progress = { numberOfStudents: 10, numberOfMasteredStudents: 3 } as CourseCompetencyProgress;
            const competency1 = { id: 1, title: 'A', courseProgress: progress } as CourseCompetency;
            const competency2 = { id: 2, title: 'B' } as CourseCompetency;
            component.courseCompetencies = [competency1, competency2];
            fixture.changeDetectorRef.detectChanges();

            competencyManagementTableComponent.sortPredicate = 'masteredStudents';

            const result = competencyManagementTableComponent.filteredAndSortedCompetencies();
            expect(result.map((c) => c.id)).toEqual([2, 1]);
        });
    });
});
