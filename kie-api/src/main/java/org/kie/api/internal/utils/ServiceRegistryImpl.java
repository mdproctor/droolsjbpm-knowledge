/*
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.kie.api.internal.utils;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an internal class, not for public consumption.
 */
public class ServiceRegistryImpl
        implements
        ServiceRegistry {

    protected static final transient Logger       logger          = LoggerFactory.getLogger( ServiceRegistryImpl.class );

    static class LazyHolder {
        static final ServiceRegistryImpl INSTANCE = new ServiceRegistryImpl();
    }

    private final Map<String, Object>             registry;

    public ServiceRegistryImpl() {
        this(ServiceDiscoveryImpl.getInstance());
    }


    public ServiceRegistryImpl(ServiceDiscoveryImpl discovery) {
        registry = discovery.getServices(this);
    }

    public synchronized <T> T get(Class<T> cls) {
        Object service = this.registry.get( cls.getName() );
        return (T) service;
    }

    /**
     * Package protected, as we only want discovery using this
     * @return
     */
    Map<String, Object> getRegistry()
    {
        return registry;
    }
}
