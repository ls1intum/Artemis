import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { NgxLearningPathNode, getIcon } from 'app/entities/competency/learning-path.model';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { ExerciseEntry, LearningPathStorageService, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-storage.service';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-learning-path',
    styleUrls: ['./learning-path.component.scss'],
    templateUrl: './learning-path.component.html',
})
export class LearningPathComponent implements OnInit {
    private learningPathService = inject(LearningPathService);
    private learningPathStorageService = inject(LearningPathStorageService);

    @Input() learningPathId: number;
    @Input() courseId: number;
    @Output() nodeClicked: EventEmitter<NgxLearningPathNode> = new EventEmitter();

    isLoading = false;
    path: NgxLearningPathNode[] = [];
    highlightedNode?: NgxLearningPathNode;

    // Icons
    faChevronDown = faChevronDown;

    protected readonly getIcon = getIcon;

    ngOnInit() {
        this.loadData();
    }

    private loadData() {
        if (!this.learningPathId) {
            return;
        }
        this.isLoading = true;
        this.learningPathService.getLearningPathNgxPath(this.learningPathId).subscribe((ngxLearningPathResponse) => {
            const body = ngxLearningPathResponse.body!;
            this.learningPathStorageService.getRecommendations(this.learningPathId)?.forEach((entry) => {
                let node;
                if (entry instanceof LectureUnitEntry) {
                    node = body.nodes.find((node) => {
                        return node.linkedResource === entry.lectureUnitId && node.linkedResourceParent === entry.lectureId;
                    });
                } else if (entry instanceof ExerciseEntry) {
                    node = body.nodes.find((node) => {
                        return node.linkedResource === entry.exerciseId && !node.linkedResourceParent;
                    });
                }
                if (node) {
                    this.path.push(node);
                }
            });
            this.isLoading = false;
        });
    }

    highlightNode(learningObject: LectureUnitEntry | ExerciseEntry) {
        if (learningObject instanceof LectureUnitEntry) {
            this.highlightedNode = this.path.find((node) => {
                return node.linkedResource === learningObject.lectureUnitId && node.linkedResourceParent === learningObject.lectureId;
            });
        } else {
            this.highlightedNode = this.path.find((node) => {
                return node.linkedResource === learningObject.exerciseId && !node.linkedResourceParent;
            });
        }
    }

    clearHighlighting() {
        this.highlightedNode = undefined;
    }
}
