/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.cloud.dataflow.server.config.features;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.registry.skipper.AppRegistrationRepository;
import org.springframework.cloud.dataflow.registry.skipper.AppRegistryService;
import org.springframework.cloud.dataflow.server.controller.VersionedAppRegistryController;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.server.repository.StreamDeploymentRepository;
import org.springframework.cloud.dataflow.server.stream.SkipperStreamDeployer;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.cloud.skipper.client.DefaultSkipperClient;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.cloud.skipper.client.SkipperClientProperties;
import org.springframework.cloud.skipper.client.SkipperClientResponseErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ForkJoinPoolFactoryBean;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Skipper related configurations.
 *
 * @author Ilayaperumal Gopinathan
 */
@Configuration
//@ConditionalOnProperty(prefix = FeaturesProperties.FEATURES_PREFIX, name = FeaturesProperties.SKIPPER_ENABLED)
public class SkipperConfiguration {

	private static Log logger = LogFactory.getLog(SkipperConfiguration.class);

//	@Bean
//	public AppRegistryService appRegistry2(AppRegistrationRepository appRegistrationRepository,
//			DelegatingResourceLoader resourceLoader) {
//		return new AppRegistryService(appRegistrationRepository, resourceLoader);
//	}

	@Bean
	public ForkJoinPoolFactoryBean appRegistryFJPFB() {
		ForkJoinPoolFactoryBean forkJoinPoolFactoryBean = new ForkJoinPoolFactoryBean();
		forkJoinPoolFactoryBean.setParallelism(4);
		return forkJoinPoolFactoryBean;
	}

//	@Bean
//	public VersionedAppRegistryController appRegistryController2(AppRegistryService appRegistry,
//			ApplicationConfigurationMetadataResolver metadataResolver) {
//		return new VersionedAppRegistryController(appRegistry, metadataResolver, appRegistryFJPFB().getObject());
//	}

	@Configuration
	@ConditionalOnBean({ StreamDefinitionRepository.class, StreamDeploymentRepository.class })
	@EnableConfigurationProperties(SkipperClientProperties.class)
	public static class SkipperStreamDeployerConfiguration {

		@Bean
		public SkipperClient skipperClient(SkipperClientProperties properties,
				RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
			RestTemplate restTemplate = restTemplateBuilder
					.errorHandler(new SkipperClientResponseErrorHandler(objectMapper))
					.messageConverters(Arrays.asList(new StringHttpMessageConverter(),
							new MappingJackson2HttpMessageConverter(objectMapper)))
					.build();
			return new DefaultSkipperClient(properties.getUri(), restTemplate);
		}

		@Bean
		public SkipperStreamDeployer skipperStreamDeployer(SkipperClient skipperClient,
				StreamDeploymentRepository streamDeploymentRepository,
				SkipperClientProperties skipperClientProperties) {
			logger.info("Skipper URI [" + skipperClientProperties.getUri() + "]");
			return new SkipperStreamDeployer(skipperClient, streamDeploymentRepository);
		}
	}
}
