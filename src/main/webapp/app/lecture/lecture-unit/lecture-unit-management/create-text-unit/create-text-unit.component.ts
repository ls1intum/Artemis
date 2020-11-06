import { Component, OnInit } from '@angular/core';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-create-text-unit',
    templateUrl: './create-text-unit.component.html',
    styles: [],
})
export class CreateTextUnitComponent implements OnInit {
    textUnitToCreate: TextUnit = new TextUnit();
    isLoading: boolean;
    lectureId: number;
    courseId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private textUnitService: TextUnitService, private alertService: JhiAlertService) {}

    ngOnInit(): void {
        this.activatedRoute.paramMap.subscribe((params) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(params.get('courseId'));
        });
        this.textUnitToCreate = new TextUnit();
    }

    createTextUnit() {
        // ToDo
    }
}
