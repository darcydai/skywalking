/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.spring.mvc.naming;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.naming.EndpointNameNamingResolver;
import org.apache.skywalking.apm.agent.core.naming.EndpointNamingControl;
import org.apache.skywalking.apm.agent.core.naming.SpanOutline;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SpringMVCEndpointNamingResolver implements EndpointNameNamingResolver {
    private static SpringMVCEndpointNamingResolver RESOLVER;

    public synchronized static void bootstrap() {
        if (RESOLVER != null) {
            return;
        }
        RESOLVER = new SpringMVCEndpointNamingResolver();
        ServiceManager.INSTANCE.findService(EndpointNamingControl.class).addResolver(RESOLVER);
    }

    public synchronized static SpringMVCEndpointNamingResolver getResolver() {
        if (RESOLVER == null) {
            bootstrap();
        }
        return RESOLVER;
    }

    private SpringMVCEndpointNamingResolver() {
    }

    private final MultiValueMap<String, SimpleRequestMappingInfo> directPath = new LinkedMultiValueMap<>();
    private final List<SimpleRequestMappingInfo> mappingInfos = new LinkedList<>();
    private String servletContextPath;
    private String servletPath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    public static final String IGNORE_HTTP_STATUS = "404";

    public void setServletContextPath(String servletContextPath) {
        this.servletContextPath = sanitizePath(servletContextPath);
    }

    public void setServletPath(String servletPath) {
        this.servletPath = sanitizePath(servletPath);
    }

    public void addMappingInfo(SimpleRequestMappingInfo info) {
        try {
            lock.writeLock().lock();
            if (info == null) {
                return;
            }
            mappingInfos.add(info);
            Set<String> paths = info.getDirectPaths();
            if (paths != null && !paths.isEmpty()) {
                paths.forEach(v -> directPath.add(v, info));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String resolve(SpanOutline spanOutline) {
        try {
            lock.readLock().lock();
            if (!spanOutline.isEntry()) {
                return null;
            }
            String originName = spanOutline.getOperationName();
            if (originName == null) {
                return null;
            }
            int componentId = spanOutline.getComponentId();
            if (componentId == 0) {
                return null;
            }
            if (componentId != ComponentsDefine.TOMCAT.getId() && componentId != ComponentsDefine.UNDERTOW.getId() && componentId != ComponentsDefine.JETTY_SERVER.getId()) {
                return null;
            }
            List<TagValuePair> tagValuePairList = spanOutline.getTags();
            Map<AbstractTag, String> tagsMap = new HashMap<>();
            if (tagValuePairList != null) {
                for (TagValuePair tagValuePair : tagValuePairList) {
                    tagsMap.put(tagValuePair.getKey(), tagValuePair.getValue());
                }
            }
            if (IGNORE_HTTP_STATUS.equals(tagsMap.get(Tags.STATUS_CODE))) {
                return null;
            }
            String lookupPath = UrlPathHelper.getLookupPath(originName, servletContextPath, servletPath);
            String method = tagsMap.get(Tags.HTTP.METHOD);
            if (directPath.containsKey(lookupPath)) {
                for (SimpleRequestMappingInfo info : directPath.get(lookupPath)) {
                    String endpointName = info.lookup(lookupPath, method);
                    if (endpointName != null) {
                        return endpointName;
                    }
                }
            }
            for (SimpleRequestMappingInfo info : mappingInfos) {
                String endpointName = info.lookup(lookupPath, method);
                if (endpointName != null) {
                    return endpointName;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    private String sanitizePath(String path) {
        if (path == null || "/*".equals(path) || "/".equals(path)) {
            return null;
        }
        if (path.endsWith("/*")) {
            path = path.substring(0, path.length() - 2);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
            return path;
        }
        return path;
    }
}
