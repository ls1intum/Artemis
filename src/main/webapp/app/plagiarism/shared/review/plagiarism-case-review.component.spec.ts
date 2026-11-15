import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { PlagiarismCaseReviewComponent } from './plagiarism-case-review.component';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { MockComponent } from 'ng-mocks';
import { provideTranslateService } from '@ngx-translate/core';
import { PlagiarismSplitViewComponent } from 'app/plagiarism/manage/plagiarism-split-view/plagiarism-split-view.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';

describe('PlagiarismCaseReviewComponent', () => {
    let component: PlagiarismCaseReviewComponent;
    let fixture: ComponentFixture<PlagiarismCaseReviewComponent>;

    const mockPlagiarismCase = {
        id: 1,
        exercise: { id: 1 } as Exercise,
        comparisons: [],
    } as PlagiarismCase;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PlagiarismCaseReviewComponent, MockComponent(PlagiarismSplitViewComponent)],
            providers: [provideTranslateService()],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCaseReviewComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with default values', () => {
        expect(component.forStudent()).toBe(true);
        expect(component.splitControlSubject).toBeInstanceOf(Subject);
    });

    it('should set plagiarismCase input', () => {
        fixture.componentRef.setInput('plagiarismCase', mockPlagiarismCase);
        expect(component.plagiarismCase()).toEqual(mockPlagiarismCase);
    });

    it('should not render split view for submissions without comparison data', () => {
        const plagiarismCaseWithoutComparison = {
            id: 1,
            exercise: { id: 1 } as Exercise,
            student: { login: 'student' },
            plagiarismSubmissions: [{ id: 1 } as PlagiarismSubmission],
        } as PlagiarismCase;

        fixture.componentRef.setInput('plagiarismCase', plagiarismCaseWithoutComparison);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('jhi-plagiarism-split-view')).toHaveLength(0);
    });

    it('should render one tab per comparison and load only the selected comparison', async () => {
        const plagiarismCase = {
            id: 1,
            exercise: { id: 1 } as Exercise,
            student: { login: 'student' },
            plagiarismSubmissions: [
                { id: 1, plagiarismComparison: { id: 11, similarity: 91.25 } },
                { id: 2, plagiarismComparison: { id: 12, similarity: 80 } },
            ] as PlagiarismSubmission[],
        } as PlagiarismCase;
        fixture.componentRef.setInput('plagiarismCase', plagiarismCase);
        fixture.detectChanges();
        await fixture.whenStable();

        const tabs = fixture.nativeElement.querySelectorAll('[role="tab"]');
        expect(tabs).toHaveLength(2);
        expect(fixture.nativeElement.querySelector('[data-testid="plagiarism-comparison-tab-0"]').textContent).toContain('1');
        expect(fixture.nativeElement.querySelectorAll('jhi-plagiarism-split-view')).toHaveLength(1);
        expect(fixture.nativeElement.textContent).toContain('91.3%');

        component.selectComparison(1);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.selectedComparison()).toBe(1);
        // the first comparison stays rendered so that its selected file and scroll position survive a switch
        expect(fixture.nativeElement.querySelectorAll('jhi-plagiarism-split-view')).toHaveLength(2);
    });

    it('should set forStudent input to false', () => {
        fixture.componentRef.setInput('forStudent', false);
        expect(component.forStudent()).toBe(false);
    });

    it('should have splitControlSubject as Subject', () => {
        expect(component.splitControlSubject).toBeInstanceOf(Subject);
        expect(component.splitControlSubject.closed).toBe(false);
    });

    it('should emit values through splitControlSubject', () => {
        const testValue = 'test-value';
        let receivedValue: string | undefined;

        component.splitControlSubject.subscribe((value) => {
            receivedValue = value;
        });

        component.splitControlSubject.next(testValue);
        expect(receivedValue).toBe(testValue);
    });
});
