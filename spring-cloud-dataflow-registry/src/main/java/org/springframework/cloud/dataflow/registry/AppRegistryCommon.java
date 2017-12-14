package org.springframework.cloud.dataflow.registry;

import java.util.List;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.core.io.Resource;

/**
 * @author Christian Tzolov
 */
public interface AppRegistryCommon {

	boolean appExist(String name, ApplicationType type);

	default boolean appExist(String name, ApplicationType type, String version) {
		throw new UnsupportedOperationException("version is not supported in Classic mode");
	}

	List<AppRegistration> findAll();

	AppRegistration find(String name, ApplicationType type);

	default AppRegistration find(String name, ApplicationType type, String version) {
		throw new UnsupportedOperationException("version is not supported in Classic mode");
	}

	Resource getAppResource(AppRegistration app);

	Resource getAppMetadataResource(AppRegistration app);
}
