import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { provideTranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseStudentRepoDownloadComponent } from 'app/programming/shared/actions/student-repo-download/programming-exercise-student-repo-download.component';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';

describe('ProgrammingExerciseStudentRepoDownloadComponent', () => {
    let comp: ProgrammingExerciseStudentRepoDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentRepoDownloadComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: HttpClient, useClass: MockHttpService },
                provideTranslateService(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseStudentRepoDownloadComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should not attempt to download if the exercise id is missing', () => {
        const exportSpy = vi.spyOn(programmingExerciseService, 'exportStudentRepository');
        fixture.componentRef.setInput('participationId', 100);
        fixture.detectChanges();
        comp.exportRepository();
        expect(exportSpy).not.toHaveBeenCalled();
    });

    it('should not attempt to download if the participation id is missing', () => {
        const exportSpy = vi.spyOn(programmingExerciseService, 'exportStudentRepository');
        fixture.componentRef.setInput('exerciseId', 100);
        fixture.detectChanges();
        comp.exportRepository();
        expect(exportSpy).not.toHaveBeenCalled();
    });

    it('should download the correct repository', () => {
        const exportSpy = vi.spyOn(programmingExerciseService, 'exportStudentRepository');
        fixture.componentRef.setInput('exerciseId', 10);
        fixture.componentRef.setInput('participationId', 20);
        fixture.detectChanges();
        comp.exportRepository();
        expect(exportSpy).toHaveBeenCalledExactlyOnceWith(10, 20);
    });
});
