import { TestBed } from '@angular/core/testing';
import { AboutIrisComponent } from 'app/iris/overview/about-iris/about-iris.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { Component } from '@angular/core';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

@Component({
    selector: 'jhi-iris-logo',
    template: '<div class="logo"></div>',
    standalone: true,
})
class IrisLogoStubComponent {}

describe('AboutIrisComponent', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AboutIrisComponent, IrisLogoStubComponent],
            providers: [
                { provide: IrisLogoComponent, useExisting: IrisLogoStubComponent },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TranslateStore, useValue: {} },
            ],
        }).compileComponents();
    });

    it('should render bullet points and logo', () => {
        const fixture = TestBed.createComponent(AboutIrisComponent);
        fixture.componentInstance.array = ((length: number) => Array.from({ length }, (_, index) => index)) as any;

        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.iris-logo')).toBeTruthy();
        expect(fixture.componentInstance.bulletPoints['1']).toBe(2);
        expect(fixture.componentInstance.objectKeys(fixture.componentInstance.bulletPoints)).toHaveLength(4);
    });
});
