import { Injectable } from '@angular/core';
import { SearchResultDTO } from './search-result-dto.model';
@Injectable()
export class SharingInfo {
    public basketToken = '';
    public returnURL: string;
    public apiBaseURL: string;
    public selectedExercise = 0;

    public isAvailable(): boolean {
        return this.basketToken !== '';
    }

    public clear(): void {
        this.basketToken = '';
    }
}

export type ShoppingBasket = {
    exerciseInfo: Array<SearchResultDTO>;
    userInfo: UserInfo;
    tokenValidUntil: number;
};

export type UserInfo = {
    email: string;
};
