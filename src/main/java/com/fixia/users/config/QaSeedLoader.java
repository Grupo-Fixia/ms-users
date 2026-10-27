package com.fixia.users.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Carga las cuentas y relaciones de QA de empresas y técnicos (GC-466), solo con el perfil {@code seed-qa}. Sin ese perfil
 * el bean no existe, así que en producción no hay forma de cargarlas por accidente: crean cuentas con una contraseña
 * conocida.
 *
 * <p>Va fuera de Flyway a propósito. Como migración repetible quedaba registrada en {@code flyway_schema_history}, y un
 * entorno que la aplicó y luego arrancaba sin el perfil dejaba de iniciar ("applied migration not resolved locally"). Aquí
 * no queda huella: quitar el perfil es seguro y los datos ya cargados se conservan. Corre después de las migraciones, y el
 * SQL es idempotente, así que repetirlo en cada arranque no duplica nada. Si una sentencia falla, se revierte todo y el
 * servicio no arranca, en vez de quedar con datos a medias.
 */
@Component
@Profile("seed-qa")
public class QaSeedLoader implements ApplicationRunner {

    static final String SCRIPT = "seed/qa_empresas.sql";

    private static final Logger log = LoggerFactory.getLogger(QaSeedLoader.class);

    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;

    public QaSeedLoader(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.transactionManager = transactionManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.warn("Perfil seed-qa activo: se cargan cuentas de QA con contraseña conocida. No debe usarse en producción.");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                new ResourceDatabasePopulator(new ClassPathResource(SCRIPT)).populate(connection);
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        });
        log.info("Datos de QA de empresas y técnicos cargados ({}).", SCRIPT);
    }
}
