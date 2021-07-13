import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlagiarismCasesComponent } from 'app/course/plagiarism-cases/plagiarism-cases.component';

describe('PlagiarismCasesComponent', () => {
    let component: PlagiarismCasesComponent;
    let fixture: ComponentFixture<PlagiarismCasesComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [PlagiarismCasesComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(PlagiarismCasesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

});
