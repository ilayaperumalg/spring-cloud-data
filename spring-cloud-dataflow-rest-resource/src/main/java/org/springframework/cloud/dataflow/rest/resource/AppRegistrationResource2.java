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

package org.springframework.cloud.dataflow.rest.resource;

import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.ResourceSupport;

/**
 * Rest resource for an app registration.
 *
 * @author Glenn Renfro
 * @author Mark Fisher
 * @author Patrick Peralta
 * @author Christian Tzolov
 */
public class AppRegistrationResource2 extends AppRegistrationResource {

	/**
	 * App version.
	 */
	private String version;

	/**
	 * Is default app version for all (name, type) applications
	 */
	private boolean isDefault;

	/**
	 * Default constructor for serialization frameworks.
	 */
	protected AppRegistrationResource2() {
	}

	/**
	 * Construct a {@code AppRegistrationResource}.
	 *
	 * @param name app name
	 * @param type app type
	 * @param version app version
	 * @param isDefault is the default app version for all (name, type) application
	 * @param uri uri for app resource
	 */
	public AppRegistrationResource2(String name, String type, String version, boolean isDefault, String uri) {
		super(name, type, uri);
		this.version = version;
		this.isDefault = isDefault;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public boolean isDefault() {
		return isDefault;
	}

	public void setDefault(boolean aDefault) {
		isDefault = aDefault;
	}

	/**
	 * Dedicated subclass to workaround type erasure.
	 */
	public static class Page extends PagedResources<AppRegistrationResource2> {
	}

}
