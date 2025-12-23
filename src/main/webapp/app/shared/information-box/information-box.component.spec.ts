import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InformationBoxComponent } from './information-box.component';
import { TranslateModule } from '@ngx-translate/core';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

describe('InformationBoxComponent', () => {
    let component: InformationBoxComponent;
    let fixture: ComponentFixture<InformationBoxComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [InformationBoxComponent, TranslateModule.forRoot(), NgbTooltipModule],
        }).compileComponents();
        fixture = TestBed.createComponent(InformationBoxComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display the title', () => {
        component.informationBoxData = {
            title: 'Test Title',
            tooltip: 'Test Tooltip',
            tooltipParams: {},
            isContentComponent: false,
            content: { type: 'string', value: 'Test Content' },
            contentColor: 'primary',
        };
        fixture.changeDetectorRef.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const titleElement = compiled.querySelector('#test-title');
        expect(titleElement?.textContent).toContain('Test Title');
    });

    it('should display the content', () => {
        component.informationBoxData = {
            title: 'Test Title',
            tooltip: 'Test Tooltip',
            tooltipParams: {},
            isContentComponent: false,
            content: { type: 'string', value: 'Test Content' },
            contentColor: 'primary',
        };
        fixture.changeDetectorRef.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const contentElement = compiled.querySelector('#test-text');
        expect(contentElement?.textContent).toContain('Test Content');
    });
});
