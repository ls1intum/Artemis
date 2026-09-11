import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionRunHeaderComponent } from './hyperion-run-header.component';

describe('HyperionRunHeaderComponent', () => {
    let fixture: ComponentFixture<HyperionRunHeaderComponent>;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HyperionRunHeaderComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(HyperionRunHeaderComponent);
        fixture.componentRef.setInput('statusState', 'running');
        fixture.componentRef.setInput('statusLabelKey', 'Running');
        fixture.componentRef.setInput('editorLink', ['/editor']);
    });

    it('withholds the editor link until the run ends', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('a[href="/editor"]')).toBeNull();
        fixture.componentRef.setInput('terminal', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('a[href="/editor"]')).not.toBeNull();
    });

    it('identifies adaptation instead of describing it as a new exercise generation', () => {
        fixture.componentRef.setInput('adapting', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-intent"]').textContent).toContain('adaptationTitle');
    });
});
