package com.estoq.core.conf.seed;

import com.estoq.business.usuario.IUsuarioRepository;
import com.estoq.business.usuario.Perfil;
import com.estoq.business.usuario.UsuarioModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria os usuários iniciais quando a tabela está vazia.
 * Admin (PIN 000000) e Cozinha (PIN 111111).
 */
@Configuration
public class SeedUsuariosConfig {

	private static final Logger log = LoggerFactory.getLogger(SeedUsuariosConfig.class);

	@Autowired
	private IUsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Bean
	@Order(1)
	public CommandLineRunner seedUsuarios() {
		return args -> {
			// Cria os usuários padrão apenas quando ainda não existem (idempotente).
			// Assim as senhas padrão sempre estão disponíveis como "fallback", sem
			// sobrescrever PINs que o administrador tenha trocado posteriormente.
			if (naoExiste("Administrador")) {
				criar("Administrador", "000000", Perfil.ADMIN);
			}
			if (naoExiste("Cozinha")) {
				criar("Cozinha", "111111", Perfil.COZINHA);
			}
			if (naoExiste("Nutricionista")) {
				criar("Nutricionista", "222222", Perfil.NUTRICIONISTA);
			}
		};
	}

	private boolean naoExiste(String nome) {
		return usuarioRepository.count() == 0
				|| !usuarioRepository.findAll().stream()
						.anyMatch(u -> nome.equalsIgnoreCase(u.getNome()));
	}

	private void criar(String nome, String pin, Perfil perfil) {
		UsuarioModel usuario = new UsuarioModel();
		usuario.setNome(nome);
		usuario.setPin(passwordEncoder.encode(pin));
		usuario.setPerfil(perfil);
		usuario.setTrocarPin(true);
		usuarioRepository.save(usuario);
		log.info("Usuário inicial criado: {} (perfil {}).", nome, perfil);
	}
}