import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { NgxLearningPathNode, getIcon } from 'app/entities/competency/learning-path.model';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { ExerciseEntry, LearningPathStorageService, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-storage.service';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-learning-path',
    styleUrls: ['./learning-path.component.scss'],
    templateUrl: './learning-path.component.html',
    encapsulation: ViewEncapsulation.None,
})
export class LearningPathComponent implements OnInit {
    @Input() learningPathId: number;
    @Input() courseId: number;

    isLoading = false;
    path?: NgxLearningPathNode[];
    highlightedNode?: NgxLearningPathNode;

    // Icons
    faChevronDown = faChevronDown;

    protected readonly getIcon = getIcon;

    constructor(
        private learningPathService: LearningPathService,
        private learningPathStorageService: LearningPathStorageService,
    ) {}

    ngOnInit() {
        this.loadData();
    }

    private loadData() {
        if (!this.learningPathId) {
            return;
        }
        this.isLoading = true;
        this.learningPathService.getLearningPathNgxPath(this.learningPathId).subscribe((ngxLearningPathResponse) => {
            this.path = [];
            const body = ngxLearningPathResponse.body!;
            this.learningPathStorageService.getRecommendations(this.learningPathId)?.forEach((entry) => {
                let node;
                if (entry instanceof LectureUnitEntry) {
                    node = body.nodes.find((node) => {
                        return node.linkedResource === entry.lectureUnitId && node.linkedResourceParent === entry.lectureId;
                    });
                } else if (entry instanceof ExerciseEntry) {
                    node = body.nodes.find((node) => {
                        return node.linkedResource === entry.exerciseId;
                    });
                }
                if (node) {
                    this.path?.push(node);
                }
            });
            this.isLoading = false;
        });
    }
}
