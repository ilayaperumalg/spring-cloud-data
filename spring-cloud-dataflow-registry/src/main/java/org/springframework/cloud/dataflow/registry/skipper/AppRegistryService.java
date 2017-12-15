package org.springframework.cloud.dataflow.registry.skipper;

import java.net.URI;
import java.util.List;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.AppRegistration;
import org.springframework.cloud.dataflow.registry.AppRegistryCommon;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * @author Christian Tzolov
 */
public interface AppRegistryService extends AppRegistryCommon {

	AppRegistration getDefaultApp(String name, ApplicationType type);

	void setDefaultApp(String name, ApplicationType type, String version);

	Page<AppRegistration> findAll(Pageable pageable);

	AppRegistration save(String name, ApplicationType type, String version, URI uri, URI metadataUri);

	void delete(String name, ApplicationType type, String version);

	List<AppRegistration> importAll(boolean overwrite, Resource... resources);

	Resource getResource(String uri);

	ResourceLoader getResourceLoader();
}
