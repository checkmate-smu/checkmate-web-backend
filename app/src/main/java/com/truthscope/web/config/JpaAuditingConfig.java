package com.truthscope.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA Auditing 설정 — @WebMvcTest 충돌 방지를 위해 별도 Config로 분리 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
