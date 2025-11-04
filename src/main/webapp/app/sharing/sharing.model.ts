import { SearchResultDTO } from './search-result-dto.model';

export class SharingInfo {
    /** Token representing the current shopping basket */
    public basketToken = '';
    /** URL to return to after completing sharing operation */
    public returnURL = '';
    /** Base URL for the sharing platform API */
    public apiBaseURL = '';
    /** ID of the currently selected exercise */
    public selectedExercise = 0;
    /** checksum for apiBaseURL and returnURL */
    public checksum = '';

    /**
     * Checks if a shopping basket is currently available
     * @returns true if a basket token exists
     */
    public isAvailable(): boolean {
        return this.basketToken !== '';
    }
    /**
     * Clears all sharing-related state
     */
    public clear(): void {
        this.basketToken = '';
        this.selectedExercise = 0;
        this.returnURL = '';
        this.apiBaseURL = '';
    }

    /**
     * Validates that all required sharing information is present
     * @throws Error if any required information is missing
     */
    public validate(): void {
        if (!this.basketToken) {
            throw new Error('Basket token is required');
        }
        if (!this.apiBaseURL) {
            throw new Error('API base URL is required');
        }
    }
}

/**
 * Represents a shopping basket containing exercises to be shared
 */
export interface ShoppingBasket {
    readonly exerciseInfo: Array<SearchResultDTO>;
    readonly userInfo: UserInfo;
    readonly tokenValidUntil: Date;
}
/**
 * Represents user information for sharing operations
 */
export interface UserInfo {
    /** User's email address for sharing notifications */
    email: string;
}
