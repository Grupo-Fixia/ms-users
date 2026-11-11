package com.fixia.users.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra las propiedades de las empresas (GC-523). */
@Configuration
@EnableConfigurationProperties(CompanyProperties.class)
public class CompanyConfig {
}
