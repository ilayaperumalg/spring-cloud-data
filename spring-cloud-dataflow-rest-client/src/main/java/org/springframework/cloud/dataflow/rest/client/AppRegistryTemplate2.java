/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.dataflow.rest.client;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.rest.resource.AppRegistrationResource;
import org.springframework.cloud.dataflow.rest.resource.DetailedAppRegistrationResource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Implementation of {@link AppRegistryOperations} that uses {@link RestTemplate} to issue
 * commands to the Data Flow server.
 *
 * @author Eric Bottard
 * @author Glenn Renfro
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Patrick Peralta
 */
public class AppRegistryTemplate2 extends AppRegistryTemplate {

	/**
	 * Construct a {@code AppRegistryTemplate} object.
	 *
	 * @param restTemplate template for HTTP/rest commands
	 * @param resourceSupport HATEOAS link support
	 */
	public AppRegistryTemplate2(RestTemplate restTemplate, ResourceSupport resourceSupport) {
		super(restTemplate, resourceSupport);
	}

	@Override
	public void unregister(String name, ApplicationType applicationType, String version) {
		String uri = uriTemplate.toString() + "/{type}/{name}/{version}";
		restTemplate.delete(uri, applicationType.name(), name, version);
	}

	@Override
	public DetailedAppRegistrationResource info(String name, ApplicationType type, String version) {
		String uri = uriTemplate.toString() + "/{type}/{name}/{version}";
		return restTemplate.getForObject(uri, DetailedAppRegistrationResource.class, type, name, version);
	}

	@Override
	public AppRegistrationResource register(String name, ApplicationType type, String version, String uri,
			String metadataUri, boolean force) {
		MultiValueMap<String, Object> values = new LinkedMultiValueMap<String, Object>();
		values.add("uri", uri);
		if (metadataUri != null) {
			values.add("metadata-uri", metadataUri);
		}
		values.add("force", Boolean.toString(force));

		return restTemplate.postForObject(uriTemplate.toString() + "/{type}/{name}/{version}", values,
				AppRegistrationResource.class, type, name, version);
	}

	@Override
	public void makeDefault(String name, ApplicationType type, String version) {
		restTemplate.put(uriTemplate.toString() + "/{type}/{name}/{version}", type, name, version);
	}
}
