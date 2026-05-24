<?php
// Invoked via `docker exec moodle php /tmp/moodle-sign-jwt.php <audience> <endpoint> <nonce> [email]`
// from NightlyLtiMoodleInteropTest. Calls Moodle's own `lti_sign_jwt()` so the JWT is bit-for-bit
// equivalent to what a real Moodle launch would produce — same RSA private key, same claim mapping,
// same kid header. The test then POSTs the returned token to Artemis to drive end-to-end interop.

define('CLI_SCRIPT', true);

if ($argc < 4) {
    fwrite(STDERR, "usage: php moodle-sign-jwt.php <audience> <endpoint> <nonce> [email]\n");
    exit(1);
}

require_once('/bitnami/moodle/config.php');
require_once($CFG->dirroot . '/mod/lti/locallib.php');

$audience = $argv[1];
$endpoint = $argv[2];
$nonce    = $argv[3];
$email    = $argv[4] ?? 'student@example.com';

$parms = [
    'lti_message_type' => 'basic-lti-launch-request',
    // Moodle's LTI_VERSION_1P3 constant — what real launches send. The IMS LTI 1.3 spec requires "1.3.0";
    // passing the LTI 1.1-style "LTI-1p3" here would be rejected by upstream OidcTokenValidator.
    'lti_version'      => '1.3.0',
    'roles'            => 'Learner',
    'user_id'          => 'moodle-user-42',
    'lis_person_contact_email_primary' => $email,
    'lis_person_name_full' => 'Test Student',
    'context_id'       => 'ctx-1',
    'context_title'    => 'Sample Course',
    'resource_link_id' => 'rl-1',
    'resource_link_title' => 'Resource',
];

$result = lti_sign_jwt($parms, $endpoint, $audience, 0, $nonce);

if (!is_array($result) || empty($result['id_token'])) {
    fwrite(STDERR, "lti_sign_jwt returned no id_token: " . var_export($result, true) . "\n");
    exit(2);
}

echo $result['id_token'];
