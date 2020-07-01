import { Component, OnInit, OnDestroy } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { SortService } from 'app/shared/service/sort.service';
import { ExportToCsv } from 'export-to-csv';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    styles: [],
})
export class ExamScoresComponent implements OnInit {
    public examScoreDTO: any;
    public exerciseGroups: any[];
    public studentResults: any[];

    public predicate = 'id';
    public reverse = false;

    constructor(private route: ActivatedRoute, private examService: ExamManagementService, private sortService: SortService) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.examService.getExamScore(params['courseId'], params['examId']).subscribe((examResponse) => {
                this.examScoreDTO = examResponse.body!;
                if (this.examScoreDTO) {
                    this.studentResults = this.examScoreDTO.studentResults;
                    this.exerciseGroups = this.examScoreDTO.exerciseGroups;
                }
            });
        });
    }

    sortRows() {
        this.sortService.sortByProperty(this.examScoreDTO.studentResults, this.predicate, this.reverse);
    }
}
