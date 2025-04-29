/*
 * Copyright © 2024-2025 Jelurida Swiss SA
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida
 * Swiss SA, no part of this software, including this file, may be copied,
 * modified, propagated, or distributed except according to the terms
 * contained in the LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.util;

import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;

import java.net.URL;

/**
 * Resource handler which operates only on system resources but allows retrieving resources with some base location
 * from all jar files in the classpath.
 * <p>
 * The original {@link ResourceHandler} works with various resource types, including system resources, but searches only
 * a single base resource URL, so cannot be used to server resources from more than one jar file
 */
public class MultiJarResourceHandler extends ResourceHandler {
    private String resourceBase;

    @Override
    public Resource getResource(String path) {
        //Jetty internally resolves any "..". Just in case, resolve them again and check if we don't .. above the root
        path = URIUtil.canonicalPath(path);
        if (path == null || !path.startsWith("/")) {
            return null;
        }
        URL systemResource = ResourceLookup.getSystemResource(resourceBase + path);
        if (systemResource == null) {
            return null;
        }
        return Resource.newResource(systemResource);
    }

    @Override
    public void setResourceBase(String resourceBase) {
        this.resourceBase = resourceBase;
    }
}
