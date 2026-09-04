package com.estoq.core.conf.seed;

import com.estoq.business.categoria.CategoriaModel;
import com.estoq.business.categoria.ICategoriaRepository;
import com.estoq.business.parametro.IParametroEstoqueRepository;
import com.estoq.business.parametro.ParametroEstoqueModel;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.UnidadeMedida;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
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
	private ICategoriaRepository categoriaRepository;

	@Autowired
	private IParametroEstoqueRepository parametroRepository;

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

			Map<String, CategoriaModel> categorias = new HashMap<>();
			int criados = 0;
			for (Map<String, Object> p : produtos) {
				String nome = ((String) p.get("nome")).trim();
				String catNome = (String) p.get("categoria");

				CategoriaModel categoria = categorias.computeIfAbsent(catNome, c -> {
					CategoriaModel nova = categoriaRepository.findByNomeIgnoreCase(c).orElseGet(() -> {
						CategoriaModel ent = new CategoriaModel();
						ent.setNome(c);
						return categoriaRepository.save(ent);
					});
					return nova;
				});

				ProdutoModel model = new ProdutoModel();
				model.setNome(nome);
				model.setUnidadeMedida(UnidadeMedida.fromLegado((String) p.get("unidade")));
				model.setCategoria(categoria);
				Object min = p.get("estoqueMinimo");
				model.setEstoqueMinimo(min instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
						: new BigDecimal(min.toString()));
				produtoRepository.save(model);

				ParametroEstoqueModel par = new ParametroEstoqueModel();
				par.setProduto(model);
				par.setTempoReposicaoDias(3);
				par.setPeriodoAnaliseDias(7);
				par.setEstoqueMinimo(model.getEstoqueMinimo());
				par.setDataAtualizacao(LocalDateTime.now());
				parametroRepository.save(par);
				criados++;
			}
			log.info("Catálogo inicial carregado: {} produtos ({} categorias).", criados, categorias.size());
		};
	}
}