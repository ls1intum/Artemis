import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { expect, vi } from 'vitest';
import { of } from 'rxjs';
import { ProgrammingExerciseVersionRepositoryCommitDiffComponent } from 'app/programming/manage/version-history/programming-exercise-version-repository-commit-diff.component';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-git-diff-report',
    template: '',
    standalone: true,
})
class MockGitDiffReportComponent {
    readonly repositoryDiffInformation = input<any>();
    readonly isRepositoryView = input<boolean>();
    readonly diffForTemplateAndSolution = input<boolean>();
    readonly leftCommitHash = input<string>();
    readonly rightCommitHash = input<string>();
    readonly participationId = input<number>();
}

describe('ProgrammingExerciseVersionRepositoryCommitDiffComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseVersionRepositoryCommitDiffComponent>;
    let component: ProgrammingExerciseVersionRepositoryCommitDiffComponent;

    const mockParticipationService = {
        getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView: vi.fn(),
    };

    beforeEach(async () => {
        // Default mock returns an observable so that the constructor subscription does not throw
        mockParticipationService.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView.mockReturnValue(of(new Map<string, string>()));

        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseVersionRepositoryCommitDiffComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProgrammingExerciseParticipationService, useValue: mockParticipationService },
            ],
        }).compileComponents();

        TestBed.overrideComponent(ProgrammingExerciseVersionRepositoryCommitDiffComponent, {
            remove: { imports: [GitDiffReportComponent] },
            add: { imports: [MockGitDiffReportComponent] },
        });

        fixture = TestBed.createComponent(ProgrammingExerciseVersionRepositoryCommitDiffComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseId', 42);
        fixture.componentRef.setInput('repositoryType', RepositoryType.TEMPLATE);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return false for shouldRender when previousCommitId is undefined', () => {
        fixture.componentRef.setInput('previousCommitId', undefined);
        fixture.componentRef.setInput('currentCommitId', 'commit-b');
        fixture.detectChanges();

        expect(component.shouldRender()).toBe(false);
    });

    it('should return false for shouldRender when currentCommitId is undefined', () => {
        fixture.componentRef.setInput('previousCommitId', 'commit-a');
        fixture.componentRef.setInput('currentCommitId', undefined);
        fixture.detectChanges();

        expect(component.shouldRender()).toBe(false);
    });

    it('should return false for shouldRender when both commit IDs are the same', () => {
        fixture.componentRef.setInput('previousCommitId', 'same-commit');
        fixture.componentRef.setInput('currentCommitId', 'same-commit');
        fixture.detectChanges();

        expect(component.shouldRender()).toBe(false);
    });

    it('should return true for shouldRender when both commit IDs are present and different', () => {
        fixture.componentRef.setInput('previousCommitId', 'commit-a');
        fixture.componentRef.setInput('currentCommitId', 'commit-b');
        fixture.detectChanges();

        expect(component.shouldRender()).toBe(true);
    });

    it('should have correct initial state', () => {
        fixture.detectChanges();

        expect(component.isLoading()).toBe(false);
        expect(component.error()).toBe(false);
        expect(component.repositoryDiffInformation()).toBeUndefined();
    });

    it('should render nothing when shouldRender is false', () => {
        fixture.componentRef.setInput('previousCommitId', undefined);
        fixture.componentRef.setInput('currentCommitId', undefined);
        fixture.detectChanges();

        expect(component.shouldRender()).toBe(false);
        // Angular leaves a <!--container--> comment for the @if block; no visible elements should be rendered
        expect(fixture.nativeElement.querySelector('.repository-commit-diff')).toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-git-diff-report')).toBeNull();
        expect(fixture.nativeElement.querySelector('p-message')).toBeNull();
        expect(fixture.nativeElement.textContent.trim()).toBe('');
    });
});
