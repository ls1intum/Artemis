import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlagiarismCasesOverviewComponent } from 'app/exam/manage/suspicious-behavior/plagiarism-cases-overview/plagiarism-cases-overview.component';

describe('PlagiarismCasesOverviewComponent', () => {
    let component: PlagiarismCasesOverviewComponent;
    let fixture: ComponentFixture<PlagiarismCasesOverviewComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [PlagiarismCasesOverviewComponent],
        });
        fixture = TestBed.createComponent(PlagiarismCasesOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
