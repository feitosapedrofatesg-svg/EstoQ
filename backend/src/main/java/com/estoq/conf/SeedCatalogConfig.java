package com.estoq.conf;

import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;

/**
 * Popula o catálogo de produtos (extraído da planilha de CMV) quando o banco está vazio.
 */
@Configuration
public class SeedCatalogConfig {

	private static final Logger log = LoggerFactory.getLogger(SeedCatalogConfig.class);

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public CommandLineRunner seedProdutos() {
		return args -> {
			if (produtoRepository.count() > 0) {
				return;
			}
			InputStream in = new ClassPathResource("seed/produtos.json").getInputStream();
			List<Map<String, Object>> produtos = objectMapper.readValue(in,
					new TypeReference<List<Map<String, Object>>>() {
					});
			int criados = 0;
			for (Map<String, Object> p : produtos) {
				ProdutoModel model = new ProdutoModel();
				model.setNome(((String) p.get("nome")).trim());
				model.setUnidade((String) p.get("unidade"));
				model.setCategoria((String) p.get("categoria"));
				Object min = p.get("estoqueMinimo");
				model.setEstoqueMinimo(min instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
						: new BigDecimal(min.toString()));
				produtoRepository.save(model);
				criados++;
			}
			log.info("Catálogo inicial carregado: {} produtos.", criados);
		};
	}
}