package de.tum.cit.aet.artemis.core.repository.webauthn;

import javax.annotation.Nonnull;

import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

public abstract class PublicKeyCredentialUserEntitySerializer implements CompactSerializer<PublicKeyCredentialUserEntity> {

    @Override
    public void write(@Nonnull CompactWriter writer, @Nonnull PublicKeyCredentialUserEntity userEntity) {
        writer.writeString("id", userEntity.getId().toBase64UrlString());
        writer.writeString("name", userEntity.getName());
        writer.writeString("displayName", userEntity.getDisplayName());
    }

    @Nonnull
    @Override
    public PublicKeyCredentialUserEntity read(@Nonnull CompactReader reader) {
        String id = reader.readString("id");
        String name = reader.readString("name");
        String displayName = reader.readString("displayName");
        // @formatter:off
        return ImmutablePublicKeyCredentialUserEntity.builder()
            .id(Bytes.fromBase64(id))
            .name(name)
            .displayName(displayName)
            .build();
        // @formatter:on
    }

    @Nonnull
    @Override
    public Class<PublicKeyCredentialUserEntity> getCompactClass() {
        return PublicKeyCredentialUserEntity.class;
    }
}
