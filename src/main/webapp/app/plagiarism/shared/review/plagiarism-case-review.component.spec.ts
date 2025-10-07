import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { PlagiarismCaseReviewComponent } from './plagiarism-case-review.component';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { MockComponent, MockModule } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { PlagiarismSplitViewComponent } from 'app/plagiarism/manage/plagiarism-split-view/plagiarism-split-view.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

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
            imports: [PlagiarismCaseReviewComponent, MockModule(NgbModule), MockModule(TranslateModule), MockComponent(PlagiarismSplitViewComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCaseReviewComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with default values', () => {
        expect(component.forStudent()).toBeTrue();
        expect(component.splitControlSubject).toBeInstanceOf(Subject);
    });

    it('should set plagiarismCase input', () => {
        fixture.componentRef.setInput('plagiarismCase', mockPlagiarismCase);
        expect(component.plagiarismCase()).toEqual(mockPlagiarismCase);
    });

    it('should set forStudent input to false', () => {
        fixture.componentRef.setInput('forStudent', false);
        expect(component.forStudent()).toBeFalse();
    });

    it('should have splitControlSubject as Subject', () => {
        expect(component.splitControlSubject).toBeInstanceOf(Subject);
        expect(component.splitControlSubject.closed).toBeFalse();
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
