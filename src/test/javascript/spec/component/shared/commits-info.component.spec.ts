import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CommitsInfoComponent } from 'app/exercises/programming/shared/commits-info/commits-info.component';

describe('CommitsInfoComponent', () => {
    let component: CommitsInfoComponent;
    let fixture: ComponentFixture<CommitsInfoComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CommitsInfoComponent],
        });
        fixture = TestBed.createComponent(CommitsInfoComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
