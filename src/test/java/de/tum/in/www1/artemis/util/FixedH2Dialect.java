package de.tum.in.www1.artemis.util;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.spi.TypeConfiguration;

public class FixedH2Dialect extends H2Dialect {

    public FixedH2Dialect() {
        this.registerColumnTypes(() -> {
            var config = new TypeConfiguration();
            var ddlType = new DdlTypeImpl(6, "real", "real", this);
            config.getDdlTypeRegistry().addDescriptor(6, ddlType);
            return config;
        }, null);
    }
}
