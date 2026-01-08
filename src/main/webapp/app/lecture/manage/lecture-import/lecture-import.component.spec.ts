import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormsModule } from '@angular/forms';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureImportComponent } from 'app/lecture/manage/lecture-import/lecture-import.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject, of } from 'rxjs';
import { LecturePagingService } from 'app/lecture/manage/services/lecture-paging.service';

describe('LectureImportComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<LectureImportComponent>;
    let comp: LectureImportComponent;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    const lecture: Lecture = {
        title: 'Lecture title',
        course: {
            title: 'Course title',
            semester: 'WS24',
        },
    };

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [
                FormsModule,
                MockComponent(NgbPagination),
                LectureImportComponent,
                MockComponent(ButtonComponent),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockComponent(FaIconComponent),
            ],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LecturePagingService, {
                    search: () => of({ resultsOnPage: [], numberOfPages: 0 }),
                }),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureImportComponent);
        comp = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        // Complete the onClose subject to clean up subscriptions
        (dialogRef.onClose as Subject<any>).complete();
    });

    it('should extract lecture properties', () => {
        expect(comp.columns).toBeDefined();
        // title, course title, semester
        expect(comp.columns).toHaveLength(3);

        const titleProperty = comp.columns[0];
        expect(titleProperty.name).toBe('TITLE');
        expect(titleProperty.getProperty(lecture)).toBe('Lecture title');

        const courseTitleProperty = comp.columns[1];
        expect(courseTitleProperty.name).toBe('COURSE_TITLE');
        expect(courseTitleProperty.getProperty(lecture)).toBe('Course title');

        const semesterProperty = comp.columns[2];
        expect(semesterProperty.name).toBe('SEMESTER');
        expect(semesterProperty.getProperty(lecture)).toBe('WS24');
    });

    it('should close dialog when selecting a lecture', () => {
        const selectedLecture = new Lecture();
        selectedLecture.id = 1;
        selectedLecture.title = 'Selected Lecture';

        comp.selectImport(selectedLecture);

        expect(dialogRefCloseSpy).toHaveBeenCalledWith(selectedLecture);
    });
});
