package de.tum.cit.aet.artemis.fileupload.domain;

import java.net.URI;
import java.nio.file.Path;

public record FilePathInformation(Path serverPath, URI publicPath, String filename) {
}
