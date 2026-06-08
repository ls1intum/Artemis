const LTI13_BASE_URL = `${location.origin}/api/lti/public/lti13`;
export const LTI_URLS = {
    LTI13_DYNAMIC_REGISTRATION_URL: `${location.origin}/lti/dynamic-registration`,
    LTI13_DEEPLINK_REDIRECT_PATH: `${LTI13_BASE_URL}/deep-link`,
    TOOL_URL: `${location.origin}/courses`,
    KEYSET_URI: `${location.origin}/.well-known/jwks.json`,
    LTI13_LOGIN_INITIATION_PATH: `${LTI13_BASE_URL}/initiate-login`,
    LTI13_LOGIN_REDIRECT_PROXY_PATH: `${LTI13_BASE_URL}/auth-callback`,
};
