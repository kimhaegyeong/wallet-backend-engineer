package com.sentbe.wallet.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger (SpringDoc OpenAPI 3) 설정 클래스
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Wallet Service API", version = "v1", description = "Sentbe Wallet Service API 명세서입니다. 출금 및 거래 내역 조회를 제공합니다."))
@SecurityScheme(name = "Bearer Authentication", type = SecuritySchemeType.HTTP, bearerFormat = "JWT", scheme = "bearer")
public class SwaggerConfig {
}
