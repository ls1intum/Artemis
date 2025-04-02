package de.tum.cit.aet.artemis.core.service;

import org.springframework.stereotype.Service;

@Service
public class PassKeyCredentialService {
    // public class PassKeyCredentialService implements WebAuthnCredentialRecordManager {
    //
    // private PasskeyCredentialsRepository authenticatorEntityRepository;
    //
    // @Autowired
    // public void setPasskeyCredentialsRepository(PasskeyCredentialsRepository authenticatorEntityRepository) {
    // this.authenticatorEntityRepository = authenticatorEntityRepository;
    // }
    //
    // private final UserRepository userRepository;
    //
    // public PassKeyCredentialService(UserRepository userRepository) {
    // this.userRepository = userRepository;
    // }
    //
    // @Override
    // public void updateCounter(byte[] credentialId, long counter) throws CredentialIdNotFoundException {
    // PasskeyCredential authenticatorEntity = authenticatorEntityRepository.findOneByCredentialId(credentialId)
    // .orElseThrow(() -> new CredentialIdNotFoundException("AuthenticatorEntity not found"));
    // authenticatorEntity.setCounter(counter);
    // }
    //
    // @Override
    // public WebAuthnCredentialRecord loadCredentialRecordByCredentialId(byte[] credentialId) {
    // return authenticatorEntityRepository.findOneByCredentialId(credentialId).orElseThrow(() -> new CredentialIdNotFoundException("AuthenticatorEntity not found"));
    // }
    //
    // @Override
    // public List<WebAuthnCredentialRecord> loadCredentialRecordsByUserPrincipal(Object principal) {
    // // FIX ME (use the proper principal and proper find all method)
    // String username;
    // if (principal == null) {
    // return Collections.emptyList();
    // }
    // else if (principal instanceof String) {
    // username = (String) principal;
    // }
    // else if (principal instanceof Authentication) {
    // username = ((Authentication) principal).getName();
    // }
    // else {
    // throw new IllegalArgumentException("unexpected principal is specified.");
    // }
    // return new ArrayList<>(authenticatorEntityRepository.findAllByEmailAddress(username));
    // }
    //
    // @Override
    // public void createCredentialRecord(WebAuthnCredentialRecord webAuthnCredentialRecord) {
    // String userPrincipal = (String) webAuthnCredentialRecord.getUserPrincipal();
    // User user = userRepository.findOneByLogin(userPrincipal).orElseThrow(() -> new EntityNotFoundException("User", userPrincipal));
    //
    // PasskeyCredential passkeyCredential = new PasskeyCredential();
    // passkeyCredential.setName(user.getName());
    // passkeyCredential.setUser(user);
    // passkeyCredential.setCounter(webAuthnCredentialRecord.getCounter());
    // passkeyCredential.setTransports(webAuthnCredentialRecord.getTransports());
    // passkeyCredential.setAttestedCredentialData(webAuthnCredentialRecord.getAttestedCredentialData());
    // passkeyCredential.setAttestationStatement(webAuthnCredentialRecord.getAttestationStatement());
    // authenticatorEntityRepository.save(passkeyCredential);
    // }
    //
    // @Override
    // public void deleteCredentialRecord(byte[] credentialId) {
    // var credentialIdString = Base64UrlUtil.encodeToString(credentialId);
    // authenticatorEntityRepository.deleteById(credentialIdString);
    // }
    //
    // @Override
    // public boolean credentialRecordExists(byte[] credentialId) {
    // var credentialIdString = Base64UrlUtil.encodeToString(credentialId);
    // return authenticatorEntityRepository.existsById(credentialIdString);
    // }
}
