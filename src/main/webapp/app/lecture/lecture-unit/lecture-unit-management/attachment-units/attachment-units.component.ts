import { Component, OnInit } from '@angular/core';
import { faBan, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { onError } from 'app/shared/util/global.utils';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';

type EntityResponseType = HttpResponse<AttachmentUnit[]>;

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

    file?: File;

    // formData = new FormData();
    // this.formData.append('file', file);

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private attachmentUnitService: AttachmentUnitService, private httpClient: HttpClient) {
        console.log(this.router.getCurrentNavigation());
        this.file = this.router.getCurrentNavigation()!.extras.state!.file;
    }

    ngOnInit(): void {
        const formData: FormData = new FormData();
        // @ts-ignore
        formData.append('file', this.file);

        console.log('test11');
        this.units = [];
        this.isProcessingMode = true;
        console.log('test22');

        this.attachmentUnitService.splitFileIntoUnits(formData).subscribe((res: any) => {
            console.log(res);
            this.units = res.body;
        });
    }

    createAttachmentUnits(param: any): void {}

    previousState() {
        this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
    }
}
