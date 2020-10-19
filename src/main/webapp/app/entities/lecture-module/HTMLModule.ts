import { LectureModule, LectureModuleType } from 'app/entities/lecture-module/lectureModule.model';

export class HTMLModule extends LectureModule {
    public markdown?: string;

    constructor() {
        super(LectureModuleType.HTML);
    }
}
