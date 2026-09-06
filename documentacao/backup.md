# EstoQ — Backup e Recuperação

Guia de backup do sistema: onde os arquivos ficam, como fazer uma cópia na
hora, o que fazer antes de desligar o computador e como recuperar em caso de erro.

---

## 1. Onde ficam os dados e os backups

| O quê | Local padrão |
|---|---|
| Banco de dados (H2) | `dados/` → o arquivo **`estoq.mv.db`** é o seu banco |
| Backups automáticos/manuais | `backups/` → arquivos **`estoq-<AAAAMMDD-HHMMSS>.zip`** |

- **Modo produção manual** (máquina de desenvolvimento): `~/estoq/distribuicao/dados` e
  `~/estoq/distribuicao/backups`.
- **Instalação desktop/launcher**: `~/.local/share/estoq/dados` e `~/.local/share/estoq/backups`
  (no Windows: `%LOCALAPPDATA%\estoq`).

A pasta de backups fica **ao lado do servidor** (`user.dir`). Um backup é uma foto compacta
do banco inteiro, gerada pelo próprio H2 (`BACKUP TO`) — **com o sistema online**, sem precisar
parar nada.

Para **migrar o sistema para outra máquina**, basta copiar a pasta `dados/`.

> Todos os backups são gerados com o servidor em execução — **não** é preciso desligar o
> sistema para fazer o backup.

---

## 2. Backup automático diário (03:30)

O sistema possui um agendamento interno (cron `0 30 3 * * *`, ativo em produção) que gera um
backup **todos os dias às 03:30** na mesma pasta `backups/`.

**Importante:** esse agendamento roda *dentro* do aplicativo. Para ele executar,
o **computador precisa estar ligado** (e o servidor de pé) **às 03:30**. Se o PC estiver
desligado nesse horário, o backup daquele dia não é gerado.

No dashboard (tela **Relatórios**) o card **"Último backup"** mostra quando foi a última
cópia e o estado **"em dia"** (verde se a cópia tem menos de 27 horas; vermelho se está
desatualizada).

---

## 3. Backup manual — o que fazer ao desligar o computador (opção 3)

Sempre que for **desligar o computador**, faça uma cópia na hora. Leva segundos:

1. Acesse **Relatórios** (perfil **Administrador**) e clique em **"Fazer backup agora"**.
   O aviso indicará o nome do arquivo criado (ex.: `estoq-20260905-213228.zip`).
2. Confirme que o card **"Último backup"** atualizou para o horário de agora (em dia).
3. Desligue o sistema de forma **limpa** (o banco fecha corretamente):
   - Pelo launcher desktop: botão **Encerrar**; ou
   - Pela API local: `curl -X POST http://localhost:8081/encerrar`.
4. Pode desligar o computador.

O agendamento das **03:30** fica como redundância extra nos dias em que o PC permanece
ligado. Com o ritual acima, você nunca fica sem uma cópia recente mesmo saindo antes das 3h.

**Outras formas de fazer um backup manual agora:**
- Botão **"Fazer backup agora"** (Relatórios ⇒ card Gestão) — reúne a mesma rotina.
- Pela API autenticada (admin):
  `curl -X POST http://localhost:8081/api/sistema/backup -H "Authorization: Bearer <TOKEN>"`.
- Pelo launcher local (sem autenticação, só na própria máquina):
  `curl -X POST http://localhost:8081/backup`.

Todas as vias geram o mesmo arquivo na pasta `backups/`.

---

## 4. Como verificar se o backup está em dia

- Tela **Relatórios** → card **"Último backup"** (verde = em dia nas últimas 27 h).
- Ou pela API (admin): `GET /api/sistema/backup/status` → `ultimoBackup`, `emDia`.
- Ou conferindo o arquivo mais recente em `backups/estoq-*.zip`.

---

## 5. Como restaurar em caso de erro

Se o banco de dados apresentar problema (corrompeu, sumiu, precisa voltar a um estado
anterior), restaure a partir do backup `.zip`:

1. **Pare o servidor** (deixe a porta 8081 livre):
   ```bash
   curl -X POST http://localhost:8081/encerrar   # responde e encerra em ~1–2 s
   # confirme que não há mais processo: a porta deve estar fechada
   ```
2. **Por segurança**, guarde uma cópia do estado atual antes de mexer:
   ```bash
   cp -r ~/estoq/distribuicao/dados /tmp/dados_antes_da_restauracao
   ```
3. **Extraia o H2** de dentro do próprio `estoq.jar` (não precisa instalar nada):
   ```bash
   mkdir -p /tmp/estoq-h2
   unzip -o ~/estoq/distribuicao/estoq.jar 'BOOT-INF/lib/h2-*.jar' -d /tmp/estoq-h2
   ```
4. **Restaure o banco** a partir do backup desejado (substitua `<DATA>` pelo arquivo real,
   ex.: `estoq-20260905-213228.zip`), na pasta `dados` do servidor:
   ```bash
   java -cp /tmp/estoq-h2/BOOT-INF/lib/h2-*.jar org.h2.tools.Restore \
     -dir ~/estoq/distribuicao/dados -db estoq \
     -file ~/estoq/distribuicao/backups/estoq-<DATA>.zip
   ```
   O comando recria `dados/estoq.mv.db` a partir do backup.
5. **Subir o servidor de novo:**
   ```bash
   cd ~/estoq/distribuicao
   java -jar estoq.jar --spring.profiles.active=prod
   ```
6. **Confira:** `curl http://localhost:8081/health` → `{"status":"UP"}` e acesse
   `http://localhost:8081` para verificar os dados.

> O backup `.zip` **não** é o banco diretamente: ele só pode ser restaurado pela ferramenta
> do H2 acima (não adianta trocar a extensão por `.mv.db`).

---

## 6. Armazenamento seguro e retenção

- **Copie o arquivo de backup para fora do computador** periodicamente: pendrive,
  rede/nuvem, ou outra máquina. Se o disco falhar, o backup precisa estar em outro lugar.
- Hoje o sistema **não apaga backups antigos** — eles se acumulam na pasta `backups/`.
  Isso é intencional (segurança), mas o espaço cresce com o tempo. Periódicamente, você pode
  apagar os `.zip` mais antigos manualmente, mantendo por exemplo a última semana/mês.
- O banco `estoq.mv.db` é o dado em uso; **não o copie a quente** como "backup", use sempre
  os `.zip` gerados (cópia a quente pode pegar o arquivo em estado intermediário).

---

## 7. Resumo rápido

| Ação | Como |
|---|---|
| Fazer cópia agora | Relatórios ⇒ "Fazer backup agora" (admin) — ou `POST /api/sistema/backup` |
| Horário automático | 03:30 diário (exige computador ligado às 03:30) |
| Antes de desligar | Backup manual ⇒ `POST /encerrar` ⇒ desligar |
| Onde está | `backups/estoq-<data>.zip` ao lado do servidor |
| Restaurar | Parar servidor ⇒ H2 `Restore` para `dados/` ⇒ subir ✔ |
| Migrar de máquina | Copiar a pasta `dados/` |