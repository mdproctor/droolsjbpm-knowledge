/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.kie.api.internal.utils;

import org.kie.api.internal.assembler.KieAssemblerService;
import org.kie.api.internal.assembler.KieAssemblers;
import org.kie.api.internal.assembler.KieAssemblersImpl;
import org.kie.api.internal.runtime.KieRuntimeService;
import org.kie.api.internal.runtime.KieRuntimes;
import org.kie.api.internal.runtime.KieRuntimesImpl;
import org.kie.api.internal.runtime.beliefs.KieBeliefService;
import org.kie.api.internal.runtime.beliefs.KieBeliefs;
import org.kie.api.internal.runtime.beliefs.KieBeliefsImpl;
import org.kie.api.internal.weaver.KieWeaverService;
import org.kie.api.internal.weaver.KieWeavers;
import org.kie.api.internal.weaver.KieWeaversImpl;
import org.mvel2.MVEL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.*;

public class ServiceDiscoveryImpl {
    private static final Logger log = LoggerFactory.getLogger( ServiceDiscoveryImpl.class );

    private final String fileName = "kie.conf";

    private final String path =  "META-INF/" + fileName;

    private ServiceDiscoveryImpl() {};

    private static class LazyHolder {
        static final ServiceDiscoveryImpl INSTANCE = new ServiceDiscoveryImpl();
    }

    public static ServiceDiscoveryImpl getInstance() {
        return LazyHolder.INSTANCE;
    }

    private Map<String, Object>     services   = new HashMap<String, Object>();
    private KieAssemblers           assemblers = new KieAssemblersImpl();
    private KieWeavers              weavers    = new KieWeaversImpl();
    private KieRuntimes             runtimes   = new KieRuntimesImpl();
    private KieBeliefs              beliefs    = new KieBeliefsImpl();
    private boolean                 sealed     = false;
    private boolean                 kiecConfDiscoveryAllowed = true;
    private Map<String, Object>     cachedServices = new HashMap<String, Object>();

    public synchronized boolean isKiecConfDiscoveryAllowed() {
        return kiecConfDiscoveryAllowed;
    }

    public synchronized void setKiecConfDiscoveryAllowed(boolean kiecConfDiscoveryAllowed) {
        this.kiecConfDiscoveryAllowed = kiecConfDiscoveryAllowed;
    }

    public synchronized  void addService(String serviceName, Object object) {
        if (!sealed) {
            cachedServices.put(serviceName, object);
        } else {
            throw new IllegalStateException("Unable to add service '" + serviceName + "'. Services cannot be added once the ServiceDiscoverys is sealed");
        }
    }

    public synchronized Map<String, Object> getServices(ServiceRegistryImpl registry) {
        if (!sealed) {
            if (kiecConfDiscoveryAllowed) {
                Enumeration<URL> confResources = null;
                try {
                    confResources = getClassLoader().getResources(path);
                } catch (Exception e) {
                    new IllegalStateException("Discovery started, but no kie.conf's found");
                }
                if (confResources != null) {
                    discover(confResources, registry);
                }
                buildMap();
            }
            cachedServices = Collections.unmodifiableMap(cachedServices);

            sealed = true;
        }
        return cachedServices;
    }


    private void discover(Enumeration<URL> confResources, ServiceRegistry serviceRegistry) {
        // iterate urls, then for each url split the service key and attempt to register each service
        while (confResources.hasMoreElements()) {
            URL                 url = confResources.nextElement();
            java.io.InputStream is  = null;
            try {
                is = url.openStream();
                log.info("Discovered kie.conf url={} ", url);
                processKieConf(is, serviceRegistry, url);
            } catch (Exception exc) {
                throw new RuntimeException("Unable to build kie service url = " + url.toExternalForm(), exc);
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    } else {
                        log.error("Unable to build kie service url={}\n", url.toExternalForm());
                    }
                } catch (IOException e1) {
                    log.warn("Unable to close Stream for url={} reason={}\n", url, e1.getMessage());
                }
            }
        }
    }

    private void processKieConf(InputStream is, ServiceRegistry serviceRegistry, URL url ) throws IOException {
        String conf = readFileAsString( new InputStreamReader( is ));
        processKieConf( conf, serviceRegistry, url );
    }

    private void processKieConf(String conf, ServiceRegistry serviceRegistry, URL url ) {
        Map<String, ?> map = ( Map<String, ?> ) MVEL.eval( conf );
        if (map == null)
        {
            log.warn("Empty kie.conf found for url={}\n", url);
            return;
        }
        processKieConf(map, serviceRegistry);
    }

    private void processKieConf(Map<String, ?> map, ServiceRegistry serviceRegistry) {
        processKieServices((Map<String, Map<String, Object>>)map);

        processKieAssemblers((Map<String, List>)map);

        processKieWeavers((Map<String, List>)map);

        processKieBeliefs((Map<String, List>)map);

        processRuntimes((Map<String, List>)map);
    }


    private void processRuntimes(Map<String, List> map) {
        List<KieRuntimeService> runtimeList = map.get( "runtimes" );
        if ( runtimeList != null && runtimeList.size() > 0 ) {
            for ( KieRuntimeService runtime : runtimeList ) {
                log.info("Adding Runtime {}\n", runtime.getServiceInterface().getName());
                runtimes.getRuntimes().put(runtime.getServiceInterface().getName(),
                                           runtime);
            }

        }
    }

    private void processKieAssemblers(Map<String, List> map) {
        List<KieAssemblerService> assemblerList = map.get( "assemblers" );
        if ( assemblerList != null && assemblerList.size() > 0 ) {
            for ( KieAssemblerService assemblerFactory : assemblerList ) {
                log.info( "Adding Assembler {}\n", assemblerFactory.getClass().getName() );
                assemblers.getAssemblers().put(assemblerFactory.getResourceType(),
                                               assemblerFactory);
            }
        }
    }

    private void processKieWeavers(Map<String, List> map) {
        List<KieWeaverService> weaverList = map.get( "weavers" );
        if ( weaverList != null && weaverList.size() > 0 ) {
            for ( KieWeaverService weaver : weaverList ) {
                log.info("Adding Weaver {}\n", weavers.getClass().getName());
                weavers.getWeavers().put( weaver.getResourceType(),
                                          weaver );
            }
        }
    }

    private void processKieBeliefs(Map<String, List> map) {
        List<KieBeliefService> beliefsList = map.get( "beliefs" );
        if ( beliefsList != null && beliefsList.size() > 0 ) {
            for ( KieBeliefService belief : beliefsList ) {
                log.info("Adding Belief {}\n", belief.getClass().getName());
                beliefs.getBeliefs().put(belief.getBeliefType(),
                                         belief);
            }
        }
    }

    private void processKieServices(Map<String, Map<String, Object>> map) {
        Map<String, Object> servicesMap = map.get( "services" );
        if ( servicesMap != null && servicesMap.size() > 0 ) {
            for ( String serviceName : servicesMap.keySet() ) {
                Object object = servicesMap.get(serviceName);
                services.put(serviceName, object);
                log.info( "Adding Service {}\n", servicesMap.get(serviceName) );
            }
        }
    }

    private String readFileAsString(Reader reader) {
        try {
            StringBuilder fileData = new StringBuilder( 1000 );
            char[] buf = new char[1024];
            int numRead;
            while ( (numRead = reader.read( buf )) != -1 ) {
                String readData = String.valueOf( buf,
                                                  0,
                                                  numRead );
                fileData.append( readData );
                buf = new char[1024];
            }
            reader.close();
            return fileData.toString();
        } catch ( IOException e ) {
            throw new RuntimeException( e );
        }
    }

    private static ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
//        if (cl == null) {
//            cl = ClassLoaderUtil.class.getClassLoader();
//        }
        return cl;
    }

    private void buildMap() {
        for (String serviceName : services.keySet()) {
            cachedServices.put(serviceName, services.get(serviceName));
        }

        cachedServices.put( KieAssemblers.class.getName(), assemblers );
        cachedServices.put( KieWeavers.class.getName(), weavers );
        cachedServices.put( KieRuntimes.class.getName(), runtimes);
        cachedServices.put( KieBeliefs.class.getName(), beliefs );
    }
}
