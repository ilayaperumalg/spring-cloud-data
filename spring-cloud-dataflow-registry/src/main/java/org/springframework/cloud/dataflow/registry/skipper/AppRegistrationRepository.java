package org.springframework.cloud.dataflow.registry.skipper;

import java.util.List;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.AppRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Christian Tzolov
 */
public interface AppRegistrationRepository extends JpaRepository<AppRegistration, Long> {

	AppRegistration findAppRegistrationByNameAndTypeAndVersion(String name, ApplicationType type, String version);

	AppRegistration findAppRegistrationByNameAndTypeAndDefaultVersionIsTrue(String name, ApplicationType type);

	void deleteAppRegistrationByNameAndTypeAndVersion(String name, ApplicationType type, String version);

	@Override
	<S extends AppRegistration> S save(S s);

	@Override
	List<AppRegistration> findAll();
}
