package com.estoq.conf;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI estoqOpenAPI() {
		return new OpenAPI().info(new Info()
				.title("estoQ API")
				.description("API do estoQ — app de gerenciamento de estoque para cozinha.\n\n"
						+ "Substitui a planilha de Cálculo de CMV Real e adiciona relatórios "
						+ "(CMV semanal/mensal, consumo e alertas de estoque mínimo).")
				.version("0.0.1-SNAPSHOT")
				.contact(new Contact().name("estoQ team")));
	}
}