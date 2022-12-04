import { Component, OnInit } from '@angular/core';
import { faBan, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
    selector: 'jhi-attachment-units',
    templateUrl: './attachment-units.component.html',
    styleUrls: [],
})
export class AttachmentUnitsComponent implements OnInit {
    isLoading: boolean;
    isProcessingMode: boolean;

    units: any[];

    faSave = faSave;
    faBan = faBan;

    constructor(private activatedRoute: ActivatedRoute, private router: Router) {}

    ngOnInit(): void {
        console.log('test11');
        this.units = [0, 1, 2, 4];
        this.isProcessingMode = true;
        console.log('test22');
    }

    createAttachmentUnits(param: any): void {}

    previousState() {
        this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
    }
}
