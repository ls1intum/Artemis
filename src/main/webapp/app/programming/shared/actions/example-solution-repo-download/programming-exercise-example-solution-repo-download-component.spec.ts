import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';

describe('ProgrammingExerciseExampleSolutionRepoDownloadComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProgrammingExerciseExampleSolutionRepoDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseExampleSolutionRepoDownloadComponent>;
    let service: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ProgrammingExerciseExampleSolutionRepoDownloadComponent, MockComponent(ButtonComponent)],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ProgrammingExerciseService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should not download when there is no exercise', () => {
        const spy = vi.spyOn(service, 'exportStudentRequestedRepository');
        component.exportRepository();
        expect(spy).not.toHaveBeenCalled();
    });

    it('should download the repos', () => {
        fixture.componentRef.setInput('exerciseId', 1);
        const exportExampleSolutionRepositorySpy = vi.spyOn(service, 'exportStudentRequestedRepository');
        component.exportRepository();
        expect(exportExampleSolutionRepositorySpy).toHaveBeenCalledOnce();
        exportExampleSolutionRepositorySpy.mockRestore();
    });
});
