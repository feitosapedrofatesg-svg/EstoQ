package com.estoq.launch;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Lançador desktop do estoQ.
 *
 * <p>Empacotado via {@code jpackage} junto com o JAR do backend e um JRE embutido.
 * O usuário final apenas clica no ícone: o launcher sobe o Spring Boot, espera o
 * {@code /health} responder, abre o navegador e mantém o sistema de pé enquanto
 * a janela estiver aberta.</p>
 *
 * <p>Os dados ficam em uma pasta de dados permanente ({{@value #DATA_SUBDIR}}),
 * separada da aplicação — atualizações trocam só o JAR e o banco é preservado.</p>
 *
 * <p>Modo de teste sem interface: {@code java -jar estoq-launcher.jar --check}
 * inicia o backend, aguarda ficar saudável, encerra e imprime no terminal.</p>
 */
public final class EstoQLauncher {

	private static final int PORTA = 8081;
	private static final String URL_NAVEGADOR = "http://localhost:" + PORTA;
	private static final String URL_SAUDE = "http://127.0.0.1:" + PORTA + "/health";
	private static final String URL_ENCERRAR = "http://127.0.0.1:" + PORTA + "/encerrar";
	private static final String NAME_JAR_BACKEND = "estoq.jar";
	private static final String NAME_JAR_LAUNCHER = "estoq-launcher.jar";
	private static final String DATA_SUBDIR = "estoq";
	private static final long TIMEOUT_SAUDE_MS = 120_000L;
	private static final long TIMEOUT_PARADA_MS = 20_000L;

	private Path dataHome;
	private Path instalHome;
	private Path jarBackend;
	private Path pidFile;
	private Path lockFile;
	private FileLock lock;
	private Process processoBackend;
	private ProcessHandle adotado;
	private JFrame janela;
	private JLabel labelStatus;
	private JButton btnAbrir;
	private JButton btnBackup;
	private JButton btnEncerrar;
	private JCheckBox chkAutostart;
	private final CountDownLatch encerrou = new CountDownLatch(1);

	public static void main(String[] args) throws Exception {
		boolean check = Arrays.asList(args).contains("--check");
		EstoQLauncher app = new EstoQLauncher();
		if (check) {
			System.exit(app.executarCheck());
		}
		SwingUtilities.invokeAndWait(app::montarInterface);
		app.encerrou.await();
	}

	// ------------------------------------------------------------------ fluxo

	private void montarInterface() {
		try {
			resolverDiretorios();
			if (instanciaJaRodando()) {
				abrirNavegador();
				System.exit(0);
				return;
			}
			if (backendJaRodando()) {
				if (!adotarBackendOrfao()) {
					System.out.println("Backend de uma sessão anterior em execução, mas sem registro. Só abrindo o navegador.");
					abrirNavegador();
					System.exit(0);
					return;
				}
				criarJanela();
				atualizarStatus("Ativo — sistema pronto em " + URL_NAVEGADOR, true);
				btnAbrir.setEnabled(true);
				btnBackup.setEnabled(true);
				abrirNavegador();
				return;
			}
			criarJanela();
			MatarProcessoAnterior.from(pidFile).rodar();
			iniciarBackend();
			novoWorker(() -> aguardarSaudavel(s -> atualizarStatus(s, true)),
					s -> posSaudeOk(), this::falhaInicializacao).execute();
		} catch (Throwable t) {
			erroFatal("Não foi possível iniciar o estoQ: " + t.getMessage());
		}
	}

	private boolean adotarBackendOrfao() {
		long pid = lerPidDoArquivo();
		if (pid <= 0) {
			return false;
		}
		return ProcessHandle.of(pid).map(h -> {
			if (h.isAlive()) {
				adotado = h;
				System.out.println("Adotando backend de sessão anterior (PID " + pid + ").");
				return Boolean.TRUE;
			}
			return Boolean.FALSE;
		}).orElse(Boolean.FALSE);
	}

	private void posSaudeOk() {
		atualizarStatus("Ativo — sistema pronto em " + URL_NAVEGADOR, true);
		btnAbrir.setEnabled(true);
		btnBackup.setEnabled(true);
		abrirNavegador();
	}

	private void falhaInicializacao(Throwable t) {
		atualizarStatus("Falha ao iniciar: " + t.getMessage(), false);
		JOptionPane.showMessageDialog(janela,
				"Não foi possível iniciar o sistema.\n\n" + t.getMessage()
						+ "\n\nVeja o log em " + dataHome.resolve("logs"),
				"estoQ — erro", JOptionPane.ERROR_MESSAGE);
		encerrar(false);
	}

	private void abrirNavegador() {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(new URI(URL_NAVEGADOR));
			} else if (ehLinux()) {
				new ProcessBuilder("xdg-open", URL_NAVEGADOR).start();
			}
		} catch (Exception e) {
			System.out.println("Não foi possível abrir o navegador automaticamente: " + e.getMessage());
			System.out.println("Acesse " + URL_NAVEGADOR + " manualmente.");
		}
	}

	private void encerrar(boolean escolhidoPeloUsuario) {
		SwingUtilities.invokeLater(() -> atualizarStatus("Encerrando...", false));
		try {
			pararBackend();
		} finally {
			liberarLock();
			encerrou.countDown();
			if (escolhidoPeloUsuario) {
				System.exit(0);
			}
		}
	}

	// ------------------------------------------------------------------ backend

	private void iniciarBackend() throws IOException {
		Path logs = dataHome.resolve("logs");
		Files.createDirectories(logs);

		String javaBin = javaBinario();
		List<String> cmd = List.of(
				javaBin, "-jar", jarBackend.toString(),
				"--spring.profiles.active=prod",
				"--spring.datasource.url=jdbc:h2:file:" + dataHome.resolve("dados").resolve("estoq")
						+ ";MODE=PostgreSQL;AUTO_SERVER=TRUE",
				"--spring.servlet.multipart.max-file-size=20MB",
				"--spring.servlet.multipart.max-request-size=20MB");

		System.out.println("Iniciando backend: " + String.join(" ", cmd));
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(dataHome.toFile());
		pb.redirectOutput(logs.resolve("estoq-backend.log").toFile());
		pb.redirectErrorStream(true);
		processoBackend = pb.start();
		Files.writeString(pidFile, String.valueOf(processoBackend.pid()), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}

	private String javaBinario() {
		String exe = ehWindows() ? "java.exe" : "java";
		Path javaHome = Paths.get(System.getProperty("java.home", ""), "bin", exe);
		if (Files.isRegularFile(javaHome)) {
			return javaHome.toString();
		}
		return exe;
	}

	private void pararBackend() {
		ProcessHandle alvo = processoBackend != null ? processoBackend.toHandle()
				: (adotado != null && adotado.isAlive() ? adotado : null);
		if (alvo == null) {
			apagarPidFile();
			return;
		}
		System.out.println("Encerrando o backend (PID " + alvo.pid() + ")...");
		pedirEncerramentoGracioso();
		alvo.destroy();
		long limite = System.currentTimeMillis() + TIMEOUT_PARADA_MS;
		while (alvo.isAlive() && System.currentTimeMillis() < limite) {
			dormir(250);
		}
		if (alvo.isAlive()) {
			System.out.println("Forçando encerramento.");
			alvo.destroyForcibly();
		}
		apagarPidFile();
	}

	/**
	 * Pede o encerramento gracioso via HTTP (o Spring fecha o H2 com segurança).
	 * No Windows o {@code ProcessHandle.destroy()} encerra o processo à força,
	 * então este endpoint é o que garante um desligamento limpo do banco.
	 * Se falhar, cai no fallback do destroy().
	 */
	private void pedirEncerramentoGracioso() {
		try {
			URL url = new URL(URL_ENCERRAR);
			URLConnection c = url.openConnection();
			c.setConnectTimeout(1500);
			c.setReadTimeout(4000);
			c.setDoOutput(true);
			c.setRequestProperty("Content-Length", "0");
			try (var out = c.getOutputStream()) { /* POST vazio */ }
			try (var in = c.getInputStream()) {
				in.readAllBytes();
			}
			System.out.println("Pedido de encerramento aceito pelo backend.");
		} catch (IOException e) {
			System.out.println("Sem resposta do /encerrar, usando kill direto: " + e.getMessage());
		}
	}

	private long lerPidDoArquivo() {
		try {
			if (Files.isRegularFile(pidFile)) {
				return Long.parseLong(Files.readString(pidFile).trim());
			}
		} catch (Exception ignored) {
			// sem pid
		}
		return 0;
	}

	private void apagarPidFile() {
		try {
			Files.deleteIfExists(pidFile);
		} catch (IOException ignored) {
			// ok
		}
	}

	private String saudavel() {
		try {
			URL url = new URL(URL_SAUDE);
			URLConnection c = url.openConnection();
			c.setConnectTimeout(1500);
			c.setReadTimeout(1500);
			try (var in = c.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private String aguardarSaudavel(Consumer<String> progresso) {
		long limite = System.currentTimeMillis() + TIMEOUT_SAUDE_MS;
		int tentativa = 0;
		while (System.currentTimeMillis() < limite) {
			String resp = saudavel();
			if (resp != null) {
				if (progresso != null) {
					progresso.accept(resp);
				}
				return resp;
			}
			tentativa++;
			if (processoBackend != null && !processoBackend.isAlive()) {
				throw new IllegalStateException("O processo do estoQ terminou antes de ficar pronto. Veja o log em "
						+ dataHome.resolve("logs").resolve("estoq-backend.log"));
			}
			if (progresso != null) {
				progresso.accept("Iniciando sistema...");
			}
			dormir(700);
		}
		throw new IllegalStateException("O sistema não ficou pronto em "
				+ (TIMEOUT_SAUDE_MS / 1000) + "s. Veja o log em "
				+ dataHome.resolve("logs").resolve("estoq-backend.log"));
	}

	private boolean backendJaRodando() {
		return saudavel() != null;
	}

	// ------------------------------------------------------------------ lock

	private boolean instanciaJaRodando() throws IOException {
		Files.createDirectories(dataHome);
		lockFile = dataHome.resolve("estoq.lock");
		FileChannel canal = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		lock = canal.tryLock();
		if (lock == null) {
			canal.close();
			return true;
		}
		return false;
	}

	private void liberarLock() {
		try {
			if (lock != null && lock.isValid()) {
				lock.release();
			}
			lock = null;
		} catch (IOException ignored) {
			// ok
		}
	}

	// ------------------------------------------------------------------ dirs

	private void resolverDiretorios() {
		dataHome = Paths.get(System.getenv().getOrDefault("ESTOQ_HOME", pastaDadosPadrao()));
		pidFile = dataHome.resolve("estoq-backend.pid");
		jarBackend = localizarJarBackend();
	}

	private String pastaDadosPadrao() {
		if (ehWindows()) {
			String local = System.getenv("LOCALAPPDATA");
			if (local != null && !local.isBlank()) {
				return Paths.get(local, DATA_SUBDIR).toString();
			}
			return Paths.get(System.getProperty("user.home"), DATA_SUBDIR).toString();
		}
		String xdg = System.getenv("XDG_DATA_HOME");
		if (xdg != null && !xdg.isBlank()) {
			return Paths.get(xdg, DATA_SUBDIR).toString();
		}
		return Paths.get(System.getProperty("user.home"), ".local", "share", DATA_SUBDIR).toString();
	}

	private Path localizarJarBackend() {
		String prop = System.getProperty("launcher.appjar");
		if (prop != null && !prop.isBlank()) {
			Path p = Paths.get(prop);
			if (Files.isRegularFile(p)) {
				return p;
			}
		}
		try {
			Path codigo = Paths.get(EstoQLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			Path pasta = codigo.toAbsolutePath().getParent();
			instalHome = pasta != null && pasta.getFileName() != null
					&& pasta.getFileName().toString().equals("app") ? pasta.getParent() : null;
			if (pasta != null) {
				Path jar = pasta.resolve(NAME_JAR_BACKEND);
				if (Files.isRegularFile(jar)) {
					return jar;
				}
			}
		} catch (Exception ignored) {
			// tenta os próximos candidatos
		}
		for (String cand : List.of("estoq.jar", "backend/target/estoq.jar", "../../backend/target/estoq.jar",
				"../backend/target/estoq.jar")) {
			Path p = Paths.get(cand);
			if (Files.isRegularFile(p)) {
				return p.toAbsolutePath();
			}
		}
		throw new IllegalStateException("Jar do estoQ não encontrado (esperado: " + NAME_JAR_BACKEND + ")");
	}

	private Path caminhoLauncherBin() {
		if (instalHome != null) {
			Path bin = instalHome.resolve("bin").resolve(ehWindows() ? "EstoQ.exe" : "EstoQ");
			if (Files.isRegularFile(bin)) {
				return bin;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ autostart

	private void aplicarAutostart(boolean ativo) {
		try {
			String perfil = System.getProperty("user.home");
			if (ehWindows()) {
				Path pasta = Paths.get(perfil, "AppData", "Roaming", "Microsoft", "Windows",
						"Start Menu", "Programs", "Startup");
				Files.createDirectories(pasta);
				Path atalho = pasta.resolve("estoq.cmd");
				Path bin = caminhoLauncherBin();
				if (ativo && bin != null) {
					Files.writeString(atalho, "@echo off\r\nstart \"\" \"" + bin + "\"\r\n");
				} else {
					Files.deleteIfExists(atalho);
				}
				return;
			}
			Path pasta = Paths.get(perfil, ".config", "autostart");
			Files.createDirectories(pasta);
			Path arq = pasta.resolve("estoq.desktop");
			if (ativo) {
				Path bin = caminhoLauncherBin();
				if (bin == null) {
					return;
				}
				Files.writeString(arq,
						"[Desktop Entry]\nType=Application\nName=EstoQ\nComment=Sistema de Gestão de Estoque\n"
								+ "Exec=" + bin + "\nTerminal=false\nX-GNOME-Autostart-enabled=true\n",
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} else {
				Files.deleteIfExists(arq);
			}
		} catch (IOException e) {
			System.out.println("Não foi possível ajustar o autostart: " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------ backup

	private String executarBackup() {
		try {
			URL url = new URL("http://127.0.0.1:" + PORTA + "/backup");
			URLConnection c = url.openConnection();
			c.setConnectTimeout(5000);
			c.setReadTimeout(120_000);
			c.setDoOutput(true);
			c.setRequestProperty("Content-Length", "0");
			try (var out = c.getOutputStream()) { /* POST vazio */ }
			try (var in = c.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			return "{\"sucesso\":false,\"mensagem\":\"" + e.getMessage() + "\"}";
		}
	}

	// ------------------------------------------------------------------ ui

	private void criarJanela() {
		janela = new JFrame("EstoQ — Gestão de Estoque");
		janela.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		janela.setResizable(false);

		JPanel painel = new JPanel();
		painel.setLayout(new GridLayout(0, 1, 0, 6));
		painel.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

		JLabel titulo = new JLabel("EstoQ");
		titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 22f));
		JLabel sub = new JLabel("Sistema de Gestão de Estoque");

		labelStatus = new JLabel("Iniciando...");
		labelStatus.setFont(labelStatus.getFont().deriveFont(Font.PLAIN, 13f));

		JPanel botoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		btnAbrir = new JButton("Abrir EstoQ");
		btnAbrir.setEnabled(false);
		btnAbrir.addActionListener(e -> abrirNavegador());
		btnBackup = new JButton("Fazer backup");
		btnBackup.setEnabled(false);
		btnBackup.addActionListener(e -> novoWorker(this::executarBackup, EstoQLauncher::mostrarBackup,
				EstoQLauncher::falhaBackup).execute());
		btnEncerrar = new JButton("Encerrar");
		btnEncerrar.addActionListener(e -> encerrar(true));
		botoes.add(btnAbrir);
		botoes.add(btnBackup);
		botoes.add(btnEncerrar);

		chkAutostart = new JCheckBox("Iniciar junto com o computador");
		chkAutostart.addActionListener(e -> aplicarAutostart(chkAutostart.isSelected()));

		painel.add(titulo);
		painel.add(sub);
		painel.add(labelStatus);
		painel.add(botoes);
		painel.add(chkAutostart);

		janela.add(painel);
		janela.setPreferredSize(new Dimension(430, 250));
		janela.pack();
		janela.setLocationRelativeTo(null);
		janela.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				encerrar(true);
			}
		});
		janela.setVisible(true);
	}

	private void atualizarStatus(String texto, boolean ok) {
		String cor = ok ? "#16703c" : "#a23a2e";
		if (labelStatus != null) {
			labelStatus.setText("<html><font color='" + cor + "'><b>●</b></font>&nbsp; " + texto + "</html>");
		}
	}

	private static void mostrarBackup(String resposta) {
		boolean ok = resposta.contains("\"sucesso\":true");
		if (ok) {
			JOptionPane.showMessageDialog(null, "Backup concluído.\n\n" + resposta, "estoQ — backup",
					JOptionPane.INFORMATION_MESSAGE);
		} else {
			JOptionPane.showMessageDialog(null, "Problema no backup:\n\n" + resposta, "estoQ — backup",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	private static void falhaBackup(Throwable t) {
		JOptionPane.showMessageDialog(null, "Erro no backup: " + t.getMessage(), "estoQ — backup",
				JOptionPane.ERROR_MESSAGE);
	}

	private static void erroFatal(String msg) {
		JOptionPane.showMessageDialog(null, msg, "estoQ", JOptionPane.ERROR_MESSAGE);
		System.exit(1);
	}

	private static <T> SwingWorker<T, Void> novoWorker(Throwing<T> acao, Consumer<T> ok, Consumer<Throwable> fail) {
		return new SwingWorker<T, Void>() {
			@Override
			protected T doInBackground() throws Exception {
				return acao.executar();
			}

			@Override
			protected void done() {
				try {
					ok.accept(get());
				} catch (Exception e) {
					fail.accept(e);
				}
			}
		};
	}

	// ------------------------------------------------------------------ modo check (testes headless)

	private int executarCheck() {
		try {
			resolverDiretorios();
			Files.createDirectories(dataHome.resolve("logs"));
			MatarProcessoAnterior.from(pidFile).rodar();
			iniciarBackend();
			aguardarSaudavel(null);
			System.out.println("ESTOQ_OK backend saudável em " + URL_NAVEGADOR);
			pararBackend();
			System.out.println("ESTOQ_SAIU encerrado com sucesso");
			return 0;
		} catch (Throwable t) {
			System.err.println("ESTOQ_FALHOU " + t.getMessage());
			try {
				pararBackend();
			} catch (Throwable ignored) {
				// já foi
			}
			return 1;
		}
	}

	// ------------------------------------------------------------------ utils

	private static boolean ehWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private static boolean ehLinux() {
		return !ehWindows() && !System.getProperty("os.name", "").toLowerCase().contains("mac");
	}

	private static void dormir(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface Throwing<T> {
		T executar() throws Exception;
	}

	/** Mata um processo registrado no pid file (resto de uma sessão anterior). */
	private static final class MatarProcessoAnterior {
		private final Path pidFile;

		private MatarProcessoAnterior(Path pidFile) {
			this.pidFile = pidFile;
		}

		static MatarProcessoAnterior from(Path pidFile) {
			return new MatarProcessoAnterior(pidFile);
		}

		void rodar() {
			long pid = lerPid();
			if (pid <= 0) {
				return;
			}
			try {
				ProcessHandle.of(pid).ifPresent(p -> {
					if (p.isAlive()) {
						System.out.println("Encerrando processo anterior do estoQ (PID " + pid + ")...");
						p.destroy();
						try {
							if (!p.onExit().get().isAlive()) {
								Thread.sleep(800);
							}
						} catch (Exception e) {
							// segue
						}
					}
				});
			} catch (Exception e) {
				System.out.println("Ignorando pid anterior: " + e.getMessage());
			}
			try {
				Files.deleteIfExists(pidFile);
			} catch (IOException ignored) {
				// ok
			}
		}

		private long lerPid() {
			try {
				if (Files.isRegularFile(pidFile)) {
					return Long.parseLong(Files.readString(pidFile).trim());
				}
			} catch (Exception ignored) {
				// sem pid
			}
			return 0;
		}
	}
}