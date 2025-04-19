import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ImportProgrammingButtonComponent } from './import-programming-button.component';

describe('ImportButtonComponent', () => {
    let component: ImportProgrammingButtonComponent;
    let fixture: ComponentFixture<ImportProgrammingButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImportProgrammingButtonComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ImportProgrammingButtonComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
