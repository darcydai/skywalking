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

package org.apache.skywalking.apm.plugin.spring.mvc.naming.interceptor;

import org.apache.catalina.core.StandardWrapper;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.plugin.spring.mvc.naming.Constants;
import org.apache.skywalking.apm.plugin.spring.mvc.naming.SpringMVCEndpointNamingResolver;

public class TomcatServletRegistrationConstructorInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        StandardWrapper wrapper = (StandardWrapper) allArguments[0];
        String servletClassName = wrapper.getServletClass();
        objInst.setSkyWalkingDynamicField(servletClassName);
        if (Constants.DISPATCHER_SERVLET_CLASS_NAME.equals(servletClassName)) {
            SpringMVCEndpointNamingResolver.getResolver().setServletContextPath(wrapper.getServletContext().getContextPath());
        }
    }
}