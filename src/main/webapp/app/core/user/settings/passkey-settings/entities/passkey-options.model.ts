// TODO guidelines type vs interface
export interface PasskeyOptions {
    rp: {
        id: string;
        name: string;
    };
    challenge: string;
    pubKeyCredParams: Array<{
        type: 'public-key';
        alg: number;
    }>;
    excludeCredentials: any[];
    authenticatorSelection: {
        requireResidentKey: boolean;
    };
    attestation?: AttestationConveyancePreference;
    extensions: {
        uvm: boolean;
        credProps: boolean;
    };
}
