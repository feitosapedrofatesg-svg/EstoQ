# Documento de Implementação — Leitura de Foto de Recibo de Mercado (OCR) no estoQ

## 1. Objetivo

Permitir que o usuário fotografe **um cupom fiscal / NFC-e / SAT de mercado** com o celular e o sistema leia a foto, extraia os itens (produto, quantidade, preço) e **grave automaticamente** as compras no banco de dados, reaproveitando/criando produtos e vinculando ao período corrente.

## 2. Decisões já tomadas

| Item | Decisão |
|------|---------|
| Tipo de recibo | Cupom fiscal / SAT / NFC-e + foto de celular |
| Motor de OCR | **Tesseract local** (open-source, gratuito) via **Tess4J** |
| Mapeamento de produtos | **Auto-mapear e gravar direto** (cria produto se não existir) |
| Projeto alvo | `estoQ/backend` (Spring Boot 4.0.5, Java 21, PostgreSQL) |
| Padrão de código | Reutilizar o módulo `integracao` existente (`ImportadorPlanilhaService`) |

## 3. Contexto do código existente (reaproveitar)

- **`/home/pedro/estoQ/backend`** — projeto Spring Boot.
- **Módulo `com.estoq.integracao`** já existente, com o padrão exato de importação:
  - `IntegracaoController` (`/api/integracao/importar-planilha`)
  - `ImportadorPlanilhaService` (lógica de importação)
  - `ResultadoImportacao` (DTO de resposta)
- **Entidades relevantes:**
  - `ProdutoModel` → `produtos` (nome, unidade, categoria, estoqueMinimo)
  - `CompraModel` → `compras` (produto_id, periodo_id, quantidade, precoUnitario, dataCompra)
  - `PeriodoModel` → `periodos` (nome, dataInicio, dataFim, status)
- **Padrões de método a copiar do `ImportadorPlanilhaService`:**
  - `obterProduto(nome, unidade, resultado)` → busca `findByNomeIgnoreCase`, cria se não existir (categoria "Importado", estoqueMinimo 7)
  - `salvarCompra(periodo, produto, qtd, preco)` → cria e salva `CompraModel`
  - `busyPeriodo(...)` → encontra ou cria período
- **Rotas:** `ProdutoController` → `/api/produtos`, `CompraController` → `/api/compras`

## 4. Etapas de implementação

### Etapa 1 — Instalar dependências do sistema (Linux Mint 22.3)

```bash
sudo apt update
sudo apt install -y tesseract-ocr tesseract-ocr-por
```

> `tesseract-ocr-por` = dicionário em **português**, essencial para cupons brasileiros. Verificar: `tesseract --list-langs` deve listar `por`.

### Etapa 2 — Adicionar dependência Java no `pom.xml`

Adicionar em `<dependencies>` do `/home/pedro/estoQ/backend/pom.xml`:

```xml
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.15.0</version>
</dependency>
```

> `tess4j` = binding Java oficial para o Tesseract (JNA), usa o binário instalado no sistema.

### Etapa 3 — Configuração no `application.properties`

Arquivo: `/home/pedro/estoQ/backend/src/main/resources/application.properties`

Adicionar:
```properties
# Configuração do Tesseract OCR
estoq.ocr.tesseract-path=/usr/bin/tesseract
estoq.ocr.data-path=/usr/share/tesseract-ocr/5/tessdata
estoq.ocr.language=por
```

> Ajustar o `data-path` conforme a versão instalada (verificar com `tesseract --version`).

### Etapa 4 — Novo pacote/módulo `com.estoq.integracao.recibo`

Criar os seguintes arquivos:

**a) `LeitorReciboService`** — recebe a imagem, chama o Tess4J, extrai o texto bruto do cupom.
```java
@Service
public class LeitorReciboService {
    public String lerTexto(MultipartFile imagem) { ... } // usa Tesseract.doOCR()
}
```

**b) `ParserReciboService`** — parseia o texto bruto do cupom:
- Extrai a **data** da compra (regex de `dd/mm/aaaa` ou `dd/mm/aa` + hora);
- Extrai cada **item**: descrição do produto, quantidade, preço unitário;
- Filtra/descarta linhas de cabeçalho, rodapé, "TOTAL", desconto, formas de pagamento, troco, etc.;
- Registra em um DTO de item extraído.

**c) `GravadorReciboService`** — grava em transação (`@Transactional`):
- Para cada item: usa **o mesmo padrão `obterProduto`** (busca por nome, cria se não existir com categoria "Recibo");
- Encontra o `PeriodoModel` aberto corrente (padrão `busyPeriodo`);
- Grava 1 `CompraModel` por item (quantidade, precoUnitario, dataCompra);
- Retorna `ResultadoImportacao` (produtosCriados, compras, mensagem).

**d) DTOs** — `ItemReciboDTO` (descricao, quantidade, precoUnitario) e reutilizar `ResultadoImportacao`.

### Etapa 5 — Novo endpoint no `IntegracaoController`

Arquivo: `/home/pedro/estoQ/backend/src/main/java/com/estoq/integracao/controller/IntegracaoController.java`

Adicionar:
```java
@PostMapping("/importar-recibo")
public ResponseEntity<ResultadoImportacao> importarRecibo(@RequestParam("arquivo") MultipartFile arquivo) {
    return ResponseEntity.ok(leitorReciboService.importar(arquivo));
}
```

> Reaproveita o limite de upload de 20MB já configurado.

### Etapa 6 — Frontend (opcional; pasta `estoQ/frontend` está vazia)

- Tela de upload de foto;
- Envia para `POST /api/integracao/importar-recibo` (multipart form-data, campo `arquivo`);
- Exibe o `ResultadoImportacao` retornado (mensagem, produtos criados, compras gravadas).

## 5. Referências de arquivos importantes

| Arquivo | Papel |
|---------|-------|
| `/home/pedro/estoQ/backend/pom.xml` | adicionar tess4j |
| `/home/pedro/estoQ/backend/src/main/resources/application.properties` | configurar OCR |
| `/home/pedro/estoQ/backend/src/main/java/com/estoq/integracao/controller/IntegracaoController.java` | novo endpoint |
| `/home/pedro/estoQ/backend/src/main/java/com/estoq/integracao/service/ImportadorPlanilhaService.java` | **fonte dos padrões** `obterProduto`/`salvarCompra`/`busyPeriodo` |
| `/home/pedro/estoQ/backend/src/main/java/com/estoq/integracao/dto/ResultadoImportacao.java` | DTO de resposta |

## 6. Verificação

1. Confirmar que `tesseract --list-langs` mostra `por`.
2. Subir o backend (PostgreSQL na porta 5433, conforme `application.properties`).
3. Enviar uma foto/cupom real via `POST /api/integracao/importar-recibo`.
4. Conferir em `produtos` (novos cadastros) e `compras` (itens gravados vinculados ao período corrente).

## 7. Observação

Como a escolha foi **auto-mapear e gravar direto**, nomes de produto lidos que não existem **criam** produtos automaticamente. Para uma versão com **revisão antes de gravar**, basta acrescentar uma etapa de confirmação entre o parse e a gravação (não incluída neste documento).
