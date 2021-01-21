import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';

describe('JhiCloneRepoButtonComponent', () => {
    let component: CloneRepoButtonComponent;
    let fixture: ComponentFixture<CloneRepoButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CloneRepoButtonComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(CloneRepoButtonComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
