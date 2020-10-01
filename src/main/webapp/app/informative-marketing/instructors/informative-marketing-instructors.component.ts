import { Component, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-informative-marketing',
    templateUrl: './informative-marketing-instructors.component.html',
    styleUrls: ['./../informative-marketing.scss'],
})
export class InformativeMarketingInstructorsComponent implements OnInit {
    features: string[];

    ngOnInit(): void {
        this.features = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
    }
}
