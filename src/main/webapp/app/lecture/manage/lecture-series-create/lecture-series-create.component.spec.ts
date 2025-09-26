import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LectureSeriesCreateComponent } from './lecture-series-create.component';

describe('LectureSeriesCreate', () => {
    let component: LectureSeriesCreateComponent;
    let fixture: ComponentFixture<LectureSeriesCreateComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureSeriesCreateComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureSeriesCreateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
