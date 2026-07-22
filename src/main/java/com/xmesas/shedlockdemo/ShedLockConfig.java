package com.xmesas.shedlockdemo;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * The "shedlock" table (see V1__create_shedlock_table.sql) is the actual coordination
 * mechanism - a real Postgres row that ANY number of independent app instances can race to
 * insert/update, with Postgres's own row-level locking deciding exactly one winner per tick.
 * Nothing here is an in-JVM mutex - it works identically whether the callers are threads in one
 * process (as simulated in this project) or separate JVMs on separate machines.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
