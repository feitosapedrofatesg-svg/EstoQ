# estoQ — Entrega (instalação no restaurante)

Sistema **autônomo**: um único arquivo (`estoq.jar`) que serve o site **e** guarda o
banco de dados. Não precisa de internet, Docker, PostgreSQL nem Node.

## Requisitos
- Um PC/notebook qualquer do restaurante (sugestão: o **PC do caixa**) com **Java 21** instalado.
  - Windows/Linux: baixe em https://adoptium.net (Temurin 21) e instale.
  - Confira no terminal/Prompt: `java -version` deve mostrar 21+.
- Rede Wi-Fi/rota local ligada. Tablet/celulares na MESMA rede do PC do caixa.

## O que tem nesta pasta
| Arquivo | Para que serve |
| --- | --- |
| `estoq.jar` | O programa inteiro (site + banco embutido) |
| `iniciar.bat` | Inicia o sistema no **Windows** (duplo clique) |
| `iniciar.sh` | Inicia o sistema no **Linux/macOS** |
| `backup.bat` / `backup.sh` | Copia de segurança do banco (fazer todo dia) |
| `dados/` | Pasta do banco (criada na primeira inicialização) |

## Passo a passo de instalação
1. Copie **esta pasta inteira** para o PC do caixa (ex.: `C:\estoq` ou `/home/usuario/estoq`).
2. Inicie o sistema (`iniciar.bat` no Windows / `./iniciar.sh` no Linux). Aguarde ~20 s
   a mensagem indicando que subiu.
3. **PC do caixa / qualquer PC**: abra o navegador em `http://localhost:8081`.
4. **Tablet da cozinha**: abra o navegador em `http://IP_DO_CAIXA:8081` (o IP aparece no
   início do sistema; exemplo `192.168.1.50`).

## Login (PINs de 6 dígitos)
| Perfil | PIN | O que faz |
| --- | --- | --- |
| **Admin** | `000000` | Tudo: produtos, compras, estoque, relatórios, períodos, usuários |
| **Cozinha** | `111111` | Só o Uso Diário (itens usados / abertos) |

> O admin pode trocar o PIN de cada usuário na tela **Usuários**. Se redefinir,
> anote o novo PIN.

## Tablet da cozinha — virar "app"
1. No navegador do tablet, abra `http://IP_DO_CAIXA:8081` e faça login (cozinha).
2. No menu do navegador escolha **"Adicionar à tela inicial"** (Chrome) /
   **"Adicionar ao ecrã inicial"** (Safari).
3. Vai aparecer um ícone **Q** na tela. Ao abrir, roda em **tela cheia**, sem barra de
   navegador — comporta como aplicativo.
4. O app sempre verifica por conta própria se há uma versão nova (atualização automática).

## Iniciar junto com o Windows (opcional, recomendado)
Para o sistema subir sozinho quando o caixa ligar:
1. Pressione `Win + R`, digite `shell:startup` e ENTER — abre a pasta de inicialização.
2. Crie um atalho para `iniciar.bat` e cole na pasta.
Se quiser, crie uma tarefa agendada: `schtasks /create /tn estoQ /tr caminho\para\iniciar.bat /sc onlogon /rl highest`.

## Iniciar automaticamente no Linux (opcional)
Como serviço. Exemplo de unidade `/etc/systemd/system/estoq.service` (ajuste `User` e o caminho):
```ini
[Unit]
Description=estoQ
After=network.target

[Service]
WorkingDirectory=/caminho/para/distribuicao
ExecStart=/caminho/para/distribuicao/iniciar.sh
Restart=on-failure
User=seu-usuario

[Install]
WantedBy=multi-user.target
```
Depois: `sudo systemctl enable --now estoq`.

## Backup diário (IMPORTANTE)
- Windows: duplo clique em `backup.bat` (ideal: fechar o estoQ antes).
- Linux: `./backup.sh`.
- Os backups caem na pasta `backups/`. Guarde-os (pendrive, nuvem).
- **Restaurar**: pare o sistema, substitua `dados/estoq.mv.db` pelo backup e inicie de novo.

## Atualizar o sistema
1. Feche/parar o sistema.
2. Substitua o arquivo `estoq.jar` pelo novo (mantenha a pasta `dados/` — é o banco).
3. Inicie novamente. Os dados ficam preservados.

## Rede — dicas de funcionamento
- O PC do caixa deve ter **IP fixo** (ou reserva de DHCP no roteador) para tablets não
  perderem o endereço.
- Libere a porta **8081** no firewall do PC do caixa apenas para a rede local:
  - Windows: `netsh advfirewall firewall add rule name="estoQ" dir=in action=allow protocol=TCP localport=8081`.
- Se mudar o IP do caixa, os aparelhos devem acessar o novo IP.

## Solução de problemas
| Problema | Solução |
| --- | --- |
| "Java não encontrado" | Instale Java 21 (Temurin) e confira `java -version` |
| Tablet não abre o site | Mesma rede? Firewall liberado? IP correto? |
| Esqueceu o PIN do admin | Peça suporte: é possível redefinir o PIN no banco `dados/estoq.mv.db` (contato do responsável) |
| Porta 8081 ocupada | Altere `--server.port=8082` no fim do comando do `iniciar.*` |

## Nota técnica
- Banco: **H2 embutido** em `dados/`. Perfil executado: `prod` (H2) via
  `--spring.profiles.active=prod`. O perfil `dev` (PostgreSQL) é só para quem desenvolve.