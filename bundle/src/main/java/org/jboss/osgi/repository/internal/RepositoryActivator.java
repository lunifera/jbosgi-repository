/*
 * #%L
 * JBossOSGi Repository
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
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
 * #L%
 */
package org.jboss.osgi.repository.internal;

import static org.jboss.osgi.repository.RepositoryMessages.MESSAGES;
import static org.jboss.osgi.repository.XRepository.SERVICE_NAMES;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.jboss.osgi.repository.RepositoryStorage;
import org.jboss.osgi.repository.RepositoryStorageFactory;
import org.jboss.osgi.repository.XRepository;
import org.jboss.osgi.repository.spi.AbstractPersistentRepository;
import org.jboss.osgi.repository.spi.AggregatingRepository;
import org.jboss.osgi.repository.spi.FileBasedRepositoryStorage;
import org.jboss.osgi.repository.spi.MavenDelegateRepository;
import org.jboss.osgi.repository.spi.MavenDelegateRepository.ConfigurationPropertyProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.repository.Repository;

/**
 * An activator for {@link Repository} services.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jan-2012
 */
public class RepositoryActivator implements BundleActivator {

    private ServiceRegistration<?> registration;

    @Override
    public void start(final BundleContext context) throws Exception {

        // Create the {@link ConfigurationPropertyProvider}
        final ConfigurationPropertyProvider propProvider = new ConfigurationPropertyProvider() {
            @Override
            public String getProperty(String key, String defaultValue) {
                String value = context.getProperty(key);
                return value != null ? value : defaultValue;
            }
        };

        // Create the {@link RepositoryStorageFactory}
        final RepositoryStorageFactory factory = new RepositoryStorageFactory() {
            @Override
            public RepositoryStorage create(XRepository repository) {
                File storageDir = getRepositoryStorageDir(context);
                return new FileBasedRepositoryStorage(repository, storageDir, propProvider);
            }
        };

        // Setup the repositories
        AggregatingRepository aggregator = new AggregatingRepository();
        aggregator.addRepository(new MavenDelegateRepository(propProvider));
        XRepository repository = new AbstractPersistentRepository(factory, aggregator);

        // Register the top level repository
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(Constants.SERVICE_DESCRIPTION, repository.getName());
        registration = context.registerService(SERVICE_NAMES, repository, props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (registration != null)
            registration.unregister();
    }

    private File getRepositoryStorageDir(BundleContext context) {
        String dirName = context.getProperty(XRepository.PROPERTY_REPOSITORY_STORAGE_DIR);
        if (dirName == null) {
            dirName = context.getProperty(Constants.FRAMEWORK_STORAGE);
            if (dirName == null) {
                try {
                    File storageDir = context.getDataFile("osgi-store");
                    dirName = storageDir.getCanonicalPath();
                } catch (IOException ex) {
                    throw MESSAGES.cannotCreateRepositoryStorageArea(ex);
                }
            }
            dirName += "/repository";
        }
        return new File(dirName).getAbsoluteFile();
    }
}