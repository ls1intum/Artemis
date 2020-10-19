import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { finalize, map } from 'rxjs/operators';
import { HTMLModule } from 'app/entities/lecture-module/HTMLModule';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureModule } from 'app/entities/lecture-module/lectureModule.model';
import { AlertService } from 'app/core/alert/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HTMLModuleService } from 'app/lecture/lecture-module/HTMLModule/htmlmodule.service';

@Component({
    selector: 'jhi-lecture-module-management',
    templateUrl: './lecture-module-management.component.html',
    styles: [],
})
export class LectureModuleManagementComponent implements OnInit {
    lectureId: number;
    lecture: Lecture;
    lectureModules: LectureModule[] = [];
    isLoading = false;

    constructor(private activatedRoute: ActivatedRoute, private lectureService: LectureService, private alertService: AlertService, private htmlModuleService: HTMLModuleService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.params.subscribe((params) => {
            this.lectureId = +params['lectureId'];
            this.loadData();
        });
    }

    loadData(): void {
        this.isLoading = true;
        if (this.lectureId) {
            this.lectureService
                .find(this.lectureId)
                .pipe(
                    map((response: HttpResponse<HTMLModule>) => response.body!),
                    finalize(() => {
                        this.isLoading = false;
                    }),
                )
                .subscribe(
                    (lecture) => {
                        this.lecture = lecture;
                        if (this.lecture?.lectureModules) {
                            this.lectureModules = this.lecture?.lectureModules;
                        }
                    },
                    (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                );
        }
    }

    createHTMLModule() {
        const newHTMLModule = new HTMLModule();
        newHTMLModule.name = 'Lorem Ipsum';
        newHTMLModule.lecture = this.lecture;
        newHTMLModule.markdown = '';
        this.isLoading = true;
        this.htmlModuleService.create(newHTMLModule).subscribe(
            () => this.loadData(),
            (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        );
    }
}
