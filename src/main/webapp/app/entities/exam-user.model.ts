import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ExamUser implements BaseEntity {
    public id?: number;
    public actualRoom?: string;
    public actualSeat?: string;
    public plannedRoom?: string;
    public plannedSeat?: string;
    public signingImagePath?: string;
    public studentImagePath?: string;
    public didCheckImage?: boolean;
    public didCheckName?: boolean;
    public didCheckLogin?: boolean;
    public didCheckRegistrationNumber?: boolean;
    public user?: User;
    public exam?: Exam;
}
