package com.fixia.users.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente HTTP hacia ms-services y propiedades de las evaluaciones (GC-444). */
@Configuration
@EnableConfigurationProperties({ServicesClientProperties.class, EvaluationProperties.class})
public class ServicesClientConfig {

    @Bean
    public RestClient servicesRestClient(ServicesClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
