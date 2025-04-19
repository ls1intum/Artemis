import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ImportQuizButtonComponent } from './import-quiz-button.component';

describe('ImportButtonComponent', () => {
    let component: ImportQuizButtonComponent;
    let fixture: ComponentFixture<ImportQuizButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImportQuizButtonComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ImportQuizButtonComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
