package com.estoq.business.usuario;

import com.estoq.business.auditoria.AuditService;
import com.estoq.core.exceptions.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceTest {

	private UsuarioService service;
	private IUsuarioRepository repository;
	private PasswordEncoder passwordEncoder;
	private AuditService auditService;

	@BeforeEach
	void setup() {
		service = new UsuarioService();
		repository = mock(IUsuarioRepository.class);
		passwordEncoder = mock(PasswordEncoder.class);
		auditService = mock(AuditService.class);
		ReflectionTestUtils.setField(service, "repository", repository);
		ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
		ReflectionTestUtils.setField(service, "auditService", auditService);
	}

	private UsuarioModel usuarioComPinPadrao() {
		UsuarioModel usuario = new UsuarioModel();
		usuario.setNome("Cozinha");
		usuario.setPerfil(Perfil.COZINHA);
		usuario.setPin("000000");
		usuario.setTrocarPin(true);
		usuario.setTentativasFalhas(3);
		usuario.setBloqueadoAte(LocalDateTime.now().plusHours(1));
		usuario.setAtivo(true);
		return usuario;
	}

	@Test
	void alterarPinEncodeSalvaResetaBloqueioEAudita() {
		UUID id = UUID.randomUUID();
		UsuarioModel usuario = usuarioComPinPadrao();
		when(repository.findByIdAndAtivoTrue(id)).thenReturn(Optional.of(usuario));
		when(repository.save(any(UsuarioModel.class))).thenReturn(usuario);
		when(passwordEncoder.encode("654321")).thenReturn("senha-encriptada");

		UsuarioModel salvo = service.alterarPin(id, "654321");

		assertEquals("senha-encriptada", salvo.getPin());
		assertFalse(salvo.isTrocarPin());
		assertEquals(0, salvo.getTentativasFalhas().intValue());
		assertNull(salvo.getBloqueadoAte());
		verify(repository).save(usuario);
		verify(auditService).registrar(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void alterarPinUsuarioInexistenteLancaErro() {
		UUID id = UUID.randomUUID();
		when(repository.findByIdAndAtivoTrue(id)).thenReturn(Optional.empty());

		assertThrows(BusinessException.class, () -> service.alterarPin(id, "654321"));
	}
}