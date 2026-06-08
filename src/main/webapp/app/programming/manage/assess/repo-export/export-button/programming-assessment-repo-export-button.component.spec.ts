import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/programming/manage/assess/repo-export/export-button/programming-assessment-repo-export-button.component';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming/manage/assess/repo-export/export-dialog/programming-assessment-repo-export-dialog.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ProgrammingAssessmentRepoExportButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: ProgrammingAssessmentRepoExportButtonComponent;
    let fixture: ComponentFixture<ProgrammingAssessmentRepoExportButtonComponent>;
    let dialogService: DialogService;

    const exerciseId = 42;
    const participationIdList = [1];
    const participantIdentifierList = 'ab12cde';
    const singleParticipantMode = false;
    const programmingExercise = new ProgrammingExercise(new Course(), undefined);
    programmingExercise.id = exerciseId;
    programmingExercise.releaseDate = dayjs();
    programmingExercise.dueDate = dayjs().add(7, 'days');

    beforeEach(() => {
        const mockDialogService = {
            open: vi.fn().mockReturnValue({ onClose: { subscribe: vi.fn() }, close: vi.fn() } as unknown as DynamicDialogRef),
        };

        return TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: mockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingAssessmentRepoExportButtonComponent);
                comp = fixture.componentInstance;
                dialogService = TestBed.inject(DialogService);

                fixture.componentRef.setInput('programmingExercises', [programmingExercise]);
                fixture.componentRef.setInput('participationIdList', participationIdList);
                fixture.componentRef.setInput('participantIdentifierList', participantIdentifierList);
                fixture.componentRef.setInput('singleParticipantMode', singleParticipantMode);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should open repo export dialog with the correct data and a dismissable config', () => {
        const openSpy = vi.spyOn(dialogService, 'open');
        const buttonPressedSpy = vi.fn();
        comp.buttonPressed.subscribe(buttonPressedSpy);

        comp.openRepoExportDialog(new MouseEvent(''));

        expect(buttonPressedSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(
            ProgrammingAssessmentRepoExportDialogComponent,
            expect.objectContaining({
                modal: true,
                closable: true,
                closeOnEscape: true,
                header: 'entity.exportRepos.title',
                data: {
                    programmingExercises: [programmingExercise],
                    participationIdList,
                    participantIdentifierList,
                    singleParticipantMode,
                },
            }),
        );
    });
});
