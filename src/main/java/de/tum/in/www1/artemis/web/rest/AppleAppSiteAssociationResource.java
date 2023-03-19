package de.tum.in.www1.artemis.web.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/.well-known")
public class AppleAppSiteAssociationResource {

    @GetMapping("/apple-app-site-association")
    public ResponseEntity<AppleAppSiteAssociation> getAppleAppSiteAssociation() {
        String[] paths = { "/courses/*" };
        Detail detail = new Detail("6444123161.de.tum.cit.artemis", paths);
        Detail[] details = { detail };
        String[] apps = {};
        Applinks applinks = new Applinks(apps, details);
        AppleAppSiteAssociation appleAppSiteAssociation = new AppleAppSiteAssociation(applinks);

        return ResponseEntity.ok(appleAppSiteAssociation);
    }
}

record AppleAppSiteAssociation(Applinks applinks) {
}

record Applinks(String[] apps, Detail[] details) {
}

record Detail(String appID, String[] paths) {
}
