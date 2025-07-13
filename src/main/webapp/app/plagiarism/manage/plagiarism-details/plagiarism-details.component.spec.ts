import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { PlagiarismDetailsComponent } from './plagiarism-details.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PlagiarismComparison } from '../../shared/entities/PlagiarismComparison';
import { Exercise } from '../../../exercise/shared/entities/exercise/exercise.model';

describe('PlagiarismDetailsComponent', () => {
    let component: PlagiarismDetailsComponent;
    let fixture: ComponentFixture<PlagiarismDetailsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PlagiarismDetailsComponent],
            providers: [provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismDetailsComponent);
        component = fixture.componentInstance;
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize splitControlSubject as a Subject', () => {
        expect(component.splitControlSubject).toBeInstanceOf(Subject);
    });

    it('should have undefined comparison by default', () => {
        fixture.detectChanges();
        expect(component.comparison()).toBeUndefined();
    });

    it('should accept comparison input and update the signal', () => {
        const mockComparison: PlagiarismComparison = {} as PlagiarismComparison;
        fixture.componentRef.setInput('comparison', mockComparison);
        expect(component.comparison()).toBe(mockComparison);
    });

    it('should accept exercise input and update the signal', () => {
        const mockExercise: Exercise = { id: 1 } as Exercise;
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.detectChanges();
        expect(component.exercise()).toBe(mockExercise);
    });
});
