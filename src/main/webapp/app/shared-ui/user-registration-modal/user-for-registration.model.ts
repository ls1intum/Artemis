export interface UserForRegistration {
    id: number;
    login: string;
    name: string;
    email?: string;
    registrationNumber?: string;
    profilePictureUrl?: string;
    isRegistered: boolean;
}

export interface UserSearchResult {
    content: UserForRegistration[];
    totalElements: number;
}
