# Leitura de cupom por foto (QR Code + OCR)

O EstoQ lê um cupom fiscal (NFC-e) a partir de uma foto/arquivo de imagem e
sugere uma entrada de estoque. A leitura **não grava nada automaticamente** —
primeiro exibe uma prévia para o usuário revisar e confirmar.

Acesso na tela **Entradas** → botão **"Entrada por cupom / nota / foto"** →
aba **Imagem**. (As abas "Colar texto" e "Arquivo XML" continuam funcionando
de forma independente.)

## Como funciona

```
Usuário seleciona/tira foto
        ↓
POST /api/cupons/ler (multipart: arquivo)
        ↓
1. QrCodeReader  → tenta ler o QR Code da NFC-e
   ├─ encontrou URL → 2
   └─ não encontrou → 3 (OCR)
        ↓
2. NfceReader → consulta a URL e tenta extrair o XML estruturado
   ├─ XML com itens → prévia (alta confiança)
   └─ falha/fiscal indisponível → 3 (OCR)   ← nunca bloqueia
        ↓
3. OcrService → tesseract do sistema (idioma "por"), **em duas frentes**:
   ├─ textual: prepara a imagem e roda com --psm 6 e --psm 4 (escolhe o melhor)
   └─ espacial: prepara com ampliação p/ 1800px de eixo maior e roda --psm 6
      com saída TSV (palavra + coordenada) → se recuperar a tabela, vence o texto
        ↓
4. CupomParser → interpreta o texto e extrai itens (qtd, preço)
   └─ caminho espacial (interpretarTabela/LeituraEspacial):
      - agrupa as palavras do TSV por linha (block/par/line) e lê por COLUNAS
        relativas à largura da imagem: código ≤0.20 L · descrição 0.14–0.56 L ·
        qtd 0.44–0.55 L · unidade 0.50–0.66 L · vl.unit 0.62–0.72 L ·
        vl.total 0.72–0.92 L (fragmentos fiscais à esquerda caem fora → ignorados)
      - número de tabela só se casar inteiro com \d{1,3}[.,]\d{1,3} (exclui "189");
        valida qtd × vl.unit ≈ vl.total e calcula confiança da linha
      - linha só-descrição vira "pendente" p/ receber os números da linha seguinte
        (nunca casa produtos de linhas diferentes); números sem descrição acima só
        anexam se estiverem próximos verticalmente
      - descarta cabeçalho de tabela e encerra a área ao ver totais/pagamento/
        tributos/rodapé — mas só depois que a tabela de itens começou (o topo do
        cupom traz "DOCUMENTO AUXILIAR DE CONSUMIDOR ELETRÔNICO")
   └─ se nada saiu do espacial, cai para o classificador textual (linha por linha,
      com pontuação de confiança, aceita apenas estrutura de produto)
        ↓
Resposta (CupomLeituraDTO): estabelecimento, data, itens, fonte, baixaConfianca
   └─ cada item traz um campo `confianca` (0.0–1.0); OCR com item
      abaixo de 0.70 marca `baixaConfianca=true`
```

A imagem é processada **em memória** (com arquivo temporário removido ao final,
no caso do OCR). **Nada de cupom é salvo no banco.** O controller devolve apenas
a prévia; a confirmação da entrada reutiliza o fluxo existente
(`POST /api/movimentacoes/entrada` + criação de produto via
`/api/integracao/criar-produto-cupom`).

## Arquivos criados (backend — `business/cupom/`)

| Classe | Responsabilidade |
|--------|------------------|
| `CupomController` | `POST /api/cupons/ler`; valida imagem (MIME, vazio, ≤10MB, dimensão); fino |
| `CupomService` | Orquestra QR → NFC-e → OCR → parser |
| `QrCodeReader` | Detecta QR Code na imagem (ZXing) |
| `NfceReader` | Consulta a URL da NFC-e e tenta extrair XML (isolado; falha → OCR) |
| `OcrService` | OCR do texto da imagem (binário `tesseract` via ProcessBuilder); prepara a imagem (tons de cinza, contraste, ampliação) e escolhe entre `--psm 6`/`--psm 4` o melhor texto; **`lerEspacial`** roda com saída TSV e devolve `LinhaOcr`/`LeituraEspacial` (palavras com posição) |
| `CupomParser` | Interpreta o texto do OCR → itens/estabelecimento/data. **Caminho espacial** (`interpretarTabela`): reconstroi a tabela por colunas relativas, com estado de linha pendente (descrição / números), stop de totais-rodapé e validação `qtd × unit ≈ total`; **fallback textual** (`interpretar`): cada linha é **classificada** com pontuação de confiança, rejeitando lixo de OCR, tributos/totais/cabeçalho/rodapé, EAN, "2 UN" e quantidade+preço BR |
| `CupomLeituraDTO` / `ItemCupomLeituraDTO` | Prévia (resposta da API); cada item inclui `confianca` |

Endpoint: `POST /api/cupons/ler` — `multipart/form-data`, campo `arquivo` (imagem).

## Configuração do OCR

O OCR **não** usa a lib nativa do tess4j (que pode divergir do Leptonica do SO);
usa o **executável `tesseract`** instalado no sistema. Requer o binário no PATH
ou em `estoq.ocr.tesseract-path`, e os dados do idioma em `estoq.ocr.data-path`.

`application-prod.properties`:
```
estoq.ocr.tesseract-path=/usr/bin/tesseract
estoq.ocr.data-path=/usr/share/tesseract-ocr/5/tessdata
estoq.ocr.language=por
```

Para instalar em outro PC (entrega standalone), o `tesseract` e o pacote de
idioma `por` devem estar presentes (no Linux: `apt install tesseract-ocr tesseract-ocr-por`).

## Dependências novas

| Biblioteca | Versão | Por quê |
|------------|--------|---------|
| `com.google.zxing:javase` | 3.5.3 | Leitura de QR Code de imagens (padrão de mercado, open source) |

`tess4j` já existia no `pom.xml` antes desta mudança e foi mantido; o `OcrService`
porém passou a chamar o binário `tesseract` diretamente (mais robusto no JAR único).

## Executar em DEV (Postgres/Docker)

```bash
docker compose up -d postgres
cd backend && mvn spring-boot:run        # dev: porta 8082
cd frontend && npm install && npm run dev   # 5173
```

## Executar em PROD (JAR único)

Mesmo fluxo do [runbook](instalacao-desktop.md) — build do front antes do jar:
```bash
cd frontend && npm run build
cd backend && mvn -q -DskipTests package
# copiar backend/target/estoq.jar para distribuicao/ e subir com perfil prod
```

## Como testar a leitura de um cupom real

1. Abra **Entradas** → **"Entrada por cupom / nota / foto"** → aba **Imagem**.
2. Tire/fotografe o cupom (papel ou tela), de preferência com boa luz e
   enquadramento, QR Code visível quando houver.
3. Aguarde o processamento (segundos).
4. Revise a prévia: produto, quantidade e preço unitário; ajuste se necessário.
5. **Confirmar e registrar** → grava as entradas (e cria produtos novos, se preciso).

## Limitações (v1)

- A NFC-e por QR depende da disponibilização do XML pelo estado/SEFAZ;
  se a consulta falhar, o sistema **cai para OCR** (nunca trava).
- O OCR depende da qualidade da foto e do `tesseract`; texto ambíguo é retornado
  com `baixaConfianca=true` para o usuário revisar.
- O parser cobre formatos brasileiros comuns (nome + quantidade + preço BR),
  com EAN e unidade ("2 UN"); exige ao menos quantidade positiva e preço real.
- Linha com nome legível mas números ilegíveis (ou reversa) pode virar item
  parcial ou "pendente" abandonado — revisar a prévia.
- Linhas que não pareçam produto (lixo de OCR, tributos, totais, rodapé) são
  **descartadas** na leitura — não viram itens de estoque. No caminho espacial,
  os encerradores fracos ("consumidor", "pagamento", "valor de total" etc.) só
  valem depois que a tabela de itens começou, para não quebrar no cabeçalho do
  cupom ("DOCUMENTO AUXILIAR DE CONSUMIDOR ELETRÔNICO").
