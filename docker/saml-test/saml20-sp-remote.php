<?php
/**
 * SAML 2.0 remote SP metadata for SimpleSAMLphp.
 *
 * See: https://simplesamlphp.org/docs/stable/simplesamlphp-reference-sp-remote
 */

$metadata['artemis'] = array(
    /*Set registration id to testidp*/
    'AssertionConsumerService' => 'http://localhost:8080/login/saml2/sso/testidp',
    'SingleLogoutService' => getenv('SIMPLESAMLPHP_SP_SINGLE_LOGOUT_SERVICE'),
    'simplesaml.nameidattribute' => 'uid',
    'NameIDFormat' => 'urn:oasis:names:tc:SAML:1.1:nameid-format:persistent',
);
