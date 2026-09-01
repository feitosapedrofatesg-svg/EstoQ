package com.estoq.conf;

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
			if (usuarioRepository.count() > 0) {
				return;
			}
			criar("Administrador", "000000", Perfil.ADMIN);
			criar("Cozinha", "111111", Perfil.COZINHA);
			log.info("Usuários iniciais criados (Administrador e Cozinha).");
		};
	}

	private void criar(String nome, String pin, Perfil perfil) {
		UsuarioModel usuario = new UsuarioModel();
		usuario.setNome(nome);
		usuario.setPin(passwordEncoder.encode(pin));
		usuario.setPerfil(perfil);
		usuarioRepository.save(usuario);
	}
}