package org.jboss.test.osgi.repository;

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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import junit.framework.Assert;

import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.repository.RepositoryStorage;
import org.jboss.osgi.repository.RepositoryStorageFactory;
import org.jboss.osgi.repository.XPersistentRepository;
import org.jboss.osgi.repository.XRepository;
import org.jboss.osgi.repository.spi.AbstractPersistentRepository;
import org.jboss.osgi.repository.spi.FileBasedRepositoryStorage;
import org.jboss.osgi.repository.spi.MavenIdentityRepository;
import org.jboss.osgi.repository.spi.MavenIdentityRepository.ConfigurationPropertyProvider;
import org.jboss.osgi.resolver.MavenCoordinates;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XRequirementBuilder;
import org.jboss.osgi.resolver.XResource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.service.repository.ContentNamespace;
import org.osgi.service.repository.RepositoryContent;

/**
 * Test the {@link AbstractPersistentRepository}
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jan-2012
 */
public class PersistentRepositoryTestCase extends AbstractRepositoryTest {

    private XPersistentRepository repository;
    private File storageDir;

    @Before
    public void setUp() throws IOException {
        storageDir = new File("./target/repository");
        deleteRecursive(storageDir);
        RepositoryStorageFactory storageFactory = new RepositoryStorageFactory() {
            public RepositoryStorage create(XRepository repository) {
                return new FileBasedRepositoryStorage(repository, storageDir, Mockito.mock(ConfigurationPropertyProvider.class));
            }
        };
        
        repository = new AbstractPersistentRepository(storageFactory);
        repository.addRepositoryDelegate(new MavenIdentityRepository());
    }

    @Test
    public void testFindProvidersByMavenId() throws Exception {

        MavenCoordinates mavenid = MavenCoordinates.parse("org.apache.felix:org.apache.felix.configadmin:1.2.8");
        XRequirement req = XRequirementBuilder.create(mavenid).getRequirement();
        Collection<Capability> caps = repository.findProviders(req);
        assertEquals("One capability", 1, caps.size());
        XCapability cap = (XCapability) caps.iterator().next();

        RepositoryStorage storage = repository.adapt(RepositoryStorage.class);
        
        // Verify that the resource is in storage
        XRequirementBuilder builder = XRequirementBuilder.create(IdentityNamespace.IDENTITY_NAMESPACE, "org.apache.felix.configadmin");
        builder.getAttributes().put(PackageNamespace.CAPABILITY_VERSION_ATTRIBUTE, "[1.0,2.0)");
        req = builder.getRequirement();
        caps = storage.findProviders(req);
        assertEquals("One capability", 1, caps.size());
        cap = (XIdentityCapability) caps.iterator().next();

        // Verify the content capability
        XResource resource = cap.getResource();
        caps = resource.getCapabilities(ContentNamespace.CONTENT_NAMESPACE);
        assertEquals("One capability", 1, caps.size());

        verifyCapability(cap);
    }

    private void verifyCapability(XCapability cap) throws IOException, MalformedURLException, BundleException {

        XResource resource = cap.getResource();
        XIdentityCapability icap = resource.getIdentityCapability();
        assertEquals("org.apache.felix.configadmin", icap.getName());
        assertEquals(Version.parseVersion("1.2.8"), icap.getVersion());
        assertEquals(IdentityNamespace.TYPE_BUNDLE, icap.getType());

        Collection<Capability> caps = resource.getCapabilities(ContentNamespace.CONTENT_NAMESPACE);
        assertEquals("One capability", 1, caps.size());
        cap = (XCapability) caps.iterator().next();
        URL url = new URL((String) cap.getAttribute(ContentNamespace.CAPABILITY_URL_ATTRIBUTE));

        String absolutePath = storageDir.getAbsolutePath();
        // Convert the absolute path so that it works on Windows too
        absolutePath = absolutePath.replace('\\', '/');
        if (!absolutePath.startsWith("/"))
            absolutePath = "/" + absolutePath;

        Assert.assertTrue("Local path: " + url, url.getPath().startsWith(absolutePath));

        RepositoryContent content = (RepositoryContent) resource;
        Manifest manifest = new JarInputStream(content.getContent()).getManifest();
        OSGiMetaData metaData = OSGiMetaDataBuilder.load(manifest);
        assertEquals("org.apache.felix.configadmin", metaData.getBundleSymbolicName());
        assertEquals(Version.parseVersion("1.2.8"), metaData.getBundleVersion());
    }
}