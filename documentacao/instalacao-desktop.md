# EstoQ — Aplicativo de Desktop

O estoQ empacotado como aplicativo desktop pronto para computador de cozinha/restaurante.
Os instaladores (`.deb` no Linux, `.msi` no Windows) contêm:

- o sistema completo (frontend + backend) num único arquivo **`estoq.jar`**;
- um **launcher** com janela própria (abrir o sistema, fazer backup, encerrar);
- um **Java 21 embutido** — a máquina **não precisa** de Java, Node, Maven, Postgres ou Docker;
- atalho no menu de aplicativos com ícone.

Fluxo de uso: o usuário clica no ícone → o launcher inicia o servidor local, espera o
`sistema ficar pronto` e abre o navegador em `http://localhost:8081`. Não se vê terminal.
O servidor escuta apenas em `127.0.0.1` (loopback).

---

## 1. Onde ficam os artefatos (saída da build)

Gerados por `scripts/empacotar.sh` em `dist/install/`:

| Arquivo | Finalidade |
|---|---|
| `estoq_1.0.0_amd64.deb` | Instalador Debian/Ubuntu (instala em `/opt/estoq` + atalho no menu) |
| `EstoQ/` | App-image: versão portátil — copie a pasta para qualquer máquina Ubuntu e execute `EstoQ/bin/EstoQ` |

## 2. Gerar o instalador (máquina de desenvolvimento)

```bash
./scripts/empacotar.sh
```

Pré-requisitos na máquina de build: Node 20+, Maven, JDK 21 (com `jpackage`, já incluso no
Temurin), `dpkg-deb`. Nenhuma dessas dependências vai para a máquina do restaurante.

## 3. Instalação numa máquina limpa

```bash
sudo dpkg -i estoq_1.0.0_amd64.deb
```

- Instala em `/opt/estoq` e registra o atalho **EstoQ** no menu de aplicativos
  (`.desktop` em `/usr/share/applications`).
- Para usar sem instalar: copie a pasta `EstoQ/` e execute `EstoQ/bin/EstoQ`.

## 4. Usar o sistema

1. Clique em **EstoQ** no menu de aplicativos.
2. A janela do launcher mostra o status; o navegador abre automaticamente em
   `http://localhost:8081`.
3. Feche **Encerrar** para desligar o servidor de forma limpa (os dados são salvos).
4. Se o servidor já estiver rodando, um segundo clique apenas reabre o navegador
   (não duplica processos).

## 5. Onde os dados ficam

Os **dados ficam fora da pasta do programa**, para que atualizações não apaguem nada:

```
~/.local/share/estoq/
├── dados/            ← banco H2 (o arquivo estoq.mv.db é o seu banco)
├── logs/             ← logs do servidor (estoq-<data>.log)
└── backups/          ← backups automáticos gerados pela janela do launcher
```

Dica: para levar o sistema a outra máquina depois, é só copiar `~/.local/share/estoq/dados`.

**No Windows**, os dados ficam em `%LOCALAPPDATA%\estoq` (mesma estrutura `dados\`, `logs\`,
`backups\`). Como o banco é o mesmo formato H2 nos dois sistemas, **migrar Linux ↔ Windows =
copiar a pasta `dados/`** de uma para a outra.

## 6. Windows (instalador `.msi`)

O código é o mesmo e já é multiplataforma (launcher, banco H2, pastas de dados, autostart).
O único detalhe é que o instalador Windows **precisa ser gerado numa máquina com Windows**,
porque o `jpackage` só produz instaladores da plataforma onde roda.

O roteiro de geração está no script `scripts/empacotar-win.ps1`. Resumo:

```powershell
# num PC Windows, dentro de scripts/ (com JDK 21+Node+Maven e WiX 3.0 instalados):
.\empacotar-win.ps1                       # gera dist\install\EstoQ-1.0.0.msi
.\empacotar-win.ps1 -Tipo app-image       # sem WiX: gera a pasta portátil EstoQ\
```

Depois:

- **Instalar:** clique duplo no `.msi` (atalhos no menu e desktop).
- **Usar/idem Linux:** clica no atalho → servidor local → navegador `http://localhost:8081`;
  "Encerrar" faz desligamento limpo do banco.
- **Dados:** `%LOCALAPPDATA%\estoq` (não são tocados por atualização).
- **Atualizar:** rode o `.msi` novo por cima (mantém dados). **Desinstalar:** em
  Configurações > Aplicativos > EstoQ.
- **Testar a pasta portátil antes de instalar:**
  `dist\install\EstoQ\bin\EstoQ.exe --check` → imprime `ESTOQ_OK` e encerra sozinho.

## 7. Atualizar a versão

1. Suba a versão, build e instale o novo pacote:

```bash
./scripts/empacotar.sh
sudo dpkg -i dist/install/estoq_1.0.0_amd64.deb   # o dpkg atualiza /opt/estoq mantendo os dados
```

2. O comando `dpkg -i` sobre a versão nova **substitui só o programa** —
   `~/.local/share/estoq/dados` não é tocado.

Atualização manual (sem .deb): substitua apenas os dois arquivos do site atual
(`/opt/estoq/lib/app/estoq.jar` e `estoq-launcher.jar`) e reinicie o app.

## 8. Backup

- **Pelo launcher:** clique em **Fazer backup** — é criado
  `~/.local/share/estoq/backups/estoq-<data>-<hora>.zip`.
- **Pela API:** `curl -X POST http://localhost:8081/backup` (gera o mesmo zip; o backup é
  feito com o servidor online, sem derrubar o sistema).
- **Pelo sistema (recomendado):** em **Relatórios ⇒ card Gestão**, clique em
  **"Fazer backup agora"** (administrador).

O funcionamento completo de backups (local automático, ritual ao desligar e restauração)
está em [`backup.md`](backup.md). Recomenda-se copiar periodicamente o arquivo de backup
para um pendrive/nuvem.

## 9. Desinstalar

```bash
sudo dpkg -r estoq
```

Isso remove o programa, **mantendo os dados** em `~/.local/share/estoq/`. Para apagar também
os dados: `rm -rf ~/.local/share/estoq` (após conferir que fez backup).

## 10. Problemas e soluções

- **Porta ocupada:** se outra instância estiver na 8081, o launcher apenas abre o navegador
  para o servidor já ativo.
- **O navegador não abre sozinho:** abra manualmente `http://localhost:8081` na mesma máquina.
- **Erros:** veja `~/.local/share/estoq/logs/estoq-<data>.log`.
- **Autostart (opcional):** a janela do launcher tem a opção "Iniciar com o sistema" —
  cria/remove `~/.config/autostart/estoq.desktop`.
- **Acesso em rede (opcional):** o prod escuta só em `127.0.0.1` por padrão. Para servir na
  rede local, defina, no arquivo de inicialização, a variável de ambiente
  `ESTOQ_ADDRESS=0.0.0.0` (use com senha forte; o login padrão é o PIN `000000`).

## 11. Executar sem instalar nada (modo manual, máquina de desenvolvimento)

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # API em :8082
cd frontend && npm install && npm run dev                          # site em :5173
```

Modo produção manual (banco em `./dados`): `java -jar target/estoq.jar --spring.profiles.active=prod`.