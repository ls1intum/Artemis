export class Account {
    constructor(
        public activated: boolean | null,
        public authorities: string[] | null,
        public email: string | null,
        public firstName: string | null,
        public langKey: string | null,
        public lastName: string | null,
        public login: string | null,
        public imageUrl: string | null,
    ) {}
}
