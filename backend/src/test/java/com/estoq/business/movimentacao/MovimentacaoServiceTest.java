package com.estoq.business.movimentacao;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.lote.ILoteRepository;
import com.estoq.business.lote.LoteModel;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.UnidadeMedida;
import com.estoq.business.produtoaberto.IProdutoAbertoRepository;
import com.estoq.business.produtoaberto.ProdutoAbertoModel;
import com.estoq.business.usuario.Perfil;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovimentacaoServiceTest {

	private MovimentacaoService service;
	private IProdutoRepository produtoRepository;
	private ILoteRepository loteRepository;
	private IProdutoAbertoRepository abertoRepository;
	private IMovimentacaoEstoqueRepository movRepository;
	private UsuarioModel usuario;

	private UUID produtoId = UUID.randomUUID();

	@BeforeEach
	void setup() {
		service = new MovimentacaoService();
		produtoRepository = mock(IProdutoRepository.class);
		loteRepository = mock(ILoteRepository.class);
		abertoRepository = mock(IProdutoAbertoRepository.class);
		movRepository = mock(IMovimentacaoEstoqueRepository.class);
		ReflectionTestUtils.setField(service, "produtoRepository", produtoRepository);
		ReflectionTestUtils.setField(service, "loteRepository", loteRepository);
		ReflectionTestUtils.setField(service, "produtoAbertoRepository", abertoRepository);
		ReflectionTestUtils.setField(service, "movRepository", movRepository);
		ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));

		usuario = new UsuarioModel();
		usuario.setNome("Teste");
		usuario.setPerfil(Perfil.ADMIN);
	}

	private ProdutoModel produto() {
		ProdutoModel p = new ProdutoModel();
		p.setNome("Arroz");
		p.setUnidadeMedida(UnidadeMedida.KG);
		ReflectionTestUtils.setField(p, "id", produtoId);
		return p;
	}

	private LoteModel lote(String codigo, BigDecimal qtd, BigDecimal preco, LocalDate validade) {
		LoteModel l = new LoteModel();
		l.setCodigo(codigo);
		l.setQuantidadeInicial(qtd);
		l.setQuantidadeAtual(qtd);
		l.setPrecoUnitario(preco);
		l.setDataValidade(validade);
		return l;
	}

	private void mockSaldo(BigDecimal saldo) {
		when(loteRepository.sumQuantidadeAtualByProdutoId(produtoId)).thenReturn(saldo);
		when(abertoRepository.sumQuantidadeRestanteByProdutoId(produtoId)).thenReturn(null);
	}

	@Test
	void consumoBaixaFifoPorVencimento() {
		when(produtoRepository.findByIdAndAtivoTrue(produtoId)).thenReturn(Optional.of(produto()));
		when(loteRepository.sumQuantidadeAtualByProdutoId(produtoId)).thenReturn(new BigDecimal("10"));
		when(abertoRepository.sumQuantidadeRestanteByProdutoId(produtoId)).thenReturn(null);
		when(abertoRepository.findAllByProduto_IdAndFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc(produtoId))
				.thenReturn(List.of());

		LoteModel primeiro = lote("E1", new BigDecimal("5"), new BigDecimal("2.00"), LocalDate.now().plusDays(3));
		LoteModel segundo = lote("E2", new BigDecimal("8"), new BigDecimal("3.00"), LocalDate.now().plusDays(10));
		when(loteRepository.findAllByProduto_IdAndAtivoTrue(produtoId)).thenReturn(List.of(primeiro, segundo));

		service.registrarConsumo(produtoId, usuario, new BigDecimal("6"), null, null);

		assertEquals(BigDecimal.ZERO, primeiro.getQuantidadeAtual());
		assertEquals(new BigDecimal("7"), segundo.getQuantidadeAtual());
	}

	@Test
	void custoConsumoUsaPrecoDoLotePrincipal() {
		when(produtoRepository.findByIdAndAtivoTrue(produtoId)).thenReturn(Optional.of(produto()));
		when(loteRepository.sumQuantidadeAtualByProdutoId(produtoId)).thenReturn(new BigDecimal("10"));
		when(abertoRepository.sumQuantidadeRestanteByProdutoId(produtoId)).thenReturn(null);
		when(abertoRepository.findAllByProduto_IdAndFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc(produtoId))
				.thenReturn(List.of());

		LoteModel lote = lote("E1", new BigDecimal("10"), new BigDecimal("4.00"), LocalDate.now().plusDays(3));
		when(loteRepository.findAllByProduto_IdAndAtivoTrue(produtoId)).thenReturn(List.of(lote));

		service.registrarConsumo(produtoId, usuario, new BigDecimal("2"), null, null);

		ArgumentCaptor<ConsumoModel> captor = ArgumentCaptor.forClass(ConsumoModel.class);
		verify(movRepository).save(captor.capture());
		assertEquals(0, new BigDecimal("8.00").compareTo(captor.getValue().getCustoConsumo()));
	}

	@Test
	void consumoUsaEmbalagemAbertaAntesDosLotes() {
		when(produtoRepository.findByIdAndAtivoTrue(produtoId)).thenReturn(Optional.of(produto()));
		when(loteRepository.sumQuantidadeAtualByProdutoId(produtoId)).thenReturn(new BigDecimal("10"));
		when(abertoRepository.sumQuantidadeRestanteByProdutoId(produtoId)).thenReturn(new BigDecimal("2"));

		ProdutoAbertoModel aberto = new ProdutoAbertoModel();
		ReflectionTestUtils.setField(aberto, "id", UUID.randomUUID());
		aberto.setQuantidadeAberta(new BigDecimal("5"));
		aberto.setQuantidadeUtilizada(BigDecimal.ZERO);
		aberto.setFinalizado(false);
		when(abertoRepository.findAllByProduto_IdAndFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc(produtoId))
				.thenReturn(List.of(aberto));

		when(loteRepository.findAllByProduto_IdAndAtivoTrue(produtoId)).thenReturn(List.of());

		MovimentacaoView view = service.registrarConsumo(produtoId, usuario, new BigDecimal("3"), null, null);

		assertEquals(new BigDecimal("3"), aberto.getQuantidadeUtilizada());
		assertEquals("CONSUMO", view.getTipo());
	}

	@Test
	void reverterEntradaNaoUtilizadaDesativaLote() {
		when(produtoRepository.findByIdAndAtivoTrue(produtoId)).thenReturn(Optional.of(produto()));
		mockSaldo(BigDecimal.ZERO);

		ArgumentCaptor<LoteModel> loteCaptor = ArgumentCaptor.forClass(LoteModel.class);
		ArgumentCaptor<EntradaModel> movCaptor = ArgumentCaptor.forClass(EntradaModel.class);

		service.registrarEntrada(produtoId, usuario, new BigDecimal("10"), new BigDecimal("50"),
				UnidadeMedida.UN, null, null, null);

		verify(loteRepository).save(loteCaptor.capture());
		verify(movRepository).save(movCaptor.capture());
		EntradaModel entrada = movCaptor.getValue();
		entrada.setLote(loteCaptor.getValue());

		when(movRepository.findByIdAndAtivoTrue(entrada.getId())).thenReturn(Optional.of(entrada));
		service.reverterMovimentacao(entrada.getId(), usuario);

		assertFalse(loteCaptor.getValue().isAtivo());
		assertFalse(entrada.isAtivo());
	}

	@Test
	void reverterEntradaUtilizadaLancaErro() {
		when(produtoRepository.findByIdAndAtivoTrue(produtoId)).thenReturn(Optional.of(produto()));
		mockSaldo(BigDecimal.ZERO);

		ArgumentCaptor<LoteModel> loteCaptor = ArgumentCaptor.forClass(LoteModel.class);
		ArgumentCaptor<EntradaModel> movCaptor = ArgumentCaptor.forClass(EntradaModel.class);
		service.registrarEntrada(produtoId, usuario, new BigDecimal("10"), new BigDecimal("50"),
				UnidadeMedida.UN, null, null, null);
		verify(loteRepository).save(loteCaptor.capture());
		verify(movRepository).save(movCaptor.capture());

		EntradaModel entrada = movCaptor.getValue();
		entrada.setLote(loteCaptor.getValue());
		loteCaptor.getValue().baixar(new BigDecimal("4"));

		when(movRepository.findByIdAndAtivoTrue(entrada.getId())).thenReturn(Optional.of(entrada));
		assertThrows(BusinessException.class,
				() -> service.reverterMovimentacao(entrada.getId(), usuario));
	}

	@Test
	void reverterConsumoCreditaOLote() {
		LoteModel lote = lote("E1", new BigDecimal("10"), new BigDecimal("3.00"), LocalDate.now().plusDays(5));
		lote.baixar(new BigDecimal("2"));

		ConsumoModel consumo = new ConsumoModel();
		ReflectionTestUtils.setField(consumo, "id", UUID.randomUUID());
		consumo.setQuantidade(new BigDecimal("2"));
		consumo.setLote(lote);
		consumo.setAtivo(true);

		when(movRepository.findByIdAndAtivoTrue(consumo.getId())).thenReturn(Optional.of(consumo));
		service.reverterMovimentacao(consumo.getId(), usuario);

		assertEquals(new BigDecimal("10"), lote.getQuantidadeAtual());
		assertFalse(consumo.isAtivo());
	}

	@Test
	void reverterDesperdicioCreditaOLote() {
		LoteModel lote = lote("E1", new BigDecimal("10"), new BigDecimal("3.00"), LocalDate.now().plusDays(5));
		lote.baixar(new BigDecimal("1"));

		DesperdicioModel desp = new DesperdicioModel();
		ReflectionTestUtils.setField(desp, "id", UUID.randomUUID());
		desp.setQuantidade(new BigDecimal("1"));
		desp.setLote(lote);
		desp.setAtivo(true);

		when(movRepository.findByIdAndAtivoTrue(desp.getId())).thenReturn(Optional.of(desp));
		service.reverterMovimentacao(desp.getId(), usuario);

		assertEquals(new BigDecimal("10"), lote.getQuantidadeAtual());
		assertFalse(desp.isAtivo());
	}

	@Test
	void entradaConvertidaPeloFator() {
		when(produtoRepository.findByIdAndAtivoTrue(produtoId)).thenReturn(Optional.of(produto()));
		mockSaldo(BigDecimal.ZERO);

		ArgumentCaptor<EntradaModel> movCaptor = ArgumentCaptor.forClass(EntradaModel.class);
		service.registrarEntrada(produtoId, usuario, new BigDecimal("2"), new BigDecimal("100"),
				UnidadeMedida.CX, new BigDecimal("12"), null, null);
		verify(movRepository).save(movCaptor.capture());

		assertEquals(0, new BigDecimal("24").compareTo(movCaptor.getValue().getQuantidade()));
		assertEquals(0, new BigDecimal("12").compareTo(movCaptor.getValue().getFatorConversao()));
	}

	@Test
	void entradaSemFatorMantemQuantidade() {
		when(produtoRepository.findByIdAndAtivoTrue(produtoId)).thenReturn(Optional.of(produto()));
		mockSaldo(BigDecimal.ZERO);

		ArgumentCaptor<EntradaModel> movCaptor = ArgumentCaptor.forClass(EntradaModel.class);
		service.registrarEntrada(produtoId, usuario, new BigDecimal("7"), null, null, null, null, null);
		verify(movRepository).save(movCaptor.capture());

		assertEquals(0, new BigDecimal("7").compareTo(movCaptor.getValue().getQuantidade()));
		assertTrue(movCaptor.getValue().getPrecoUnitario() != null);
	}
}