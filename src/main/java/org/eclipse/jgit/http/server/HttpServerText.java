/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com> and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Translation bundle for JGit http server
 */
public class HttpServerText extends TranslationBundle {

    /**
     * Get an instance of this translation bundle
     *
     * @return an instance of this translation bundle
     */
    public static HttpServerText get() {
        return NLS.getBundleFor(HttpServerText.class);
    }

    // @formatter:off
	/***/ public String alreadyInitializedByContainer;
	/***/ public String cannotGetLengthOf;
	/***/ public String encodingNotSupportedByThisLibrary;
	/***/ public String expectedRepositoryAttribute;
	/***/ public String filterMustNotBeNull;
	/***/ public String internalErrorDuringReceivePack;
	/***/ public String internalErrorDuringUploadPack;
	/***/ public String internalServerError;
	/***/ public String internalServerErrorRequestAttributeWasAlreadySet;
	/***/ public String invalidBoolean;
	/***/ public String invalidIndex;
	/***/ public String invalidRegexGroup;
	/***/ public String noResolverAvailable;
	/***/ public String parameterNotSet;
	/***/ public String pathForParamNotFound;
	/***/ public String pathNotSupported;
	/***/ public String receivedCorruptObject;
	/***/ public String repositoryAccessForbidden;
	/***/ public String repositoryNotFound;
	/***/ public String servletAlreadyInitialized;
	/***/ public String servletMustNotBeNull;
	/***/ public String servletWasAlreadyBound;
	/***/ public String unexpectedeOFOn;
}
