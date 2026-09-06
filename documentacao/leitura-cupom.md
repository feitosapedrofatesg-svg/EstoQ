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
3. OcrService → tesseract do sistema (idioma "por")
   ├─ prepara a imagem (tons de cinza + contraste + ampliação)
   └─ roda com --psm 6 e --psm 4 e escolhe o melhor texto
        ↓
4. CupomParser → interpreta o texto e extrai itens (qtd, preço)
   └─ ignora linhas de rodapé/tributos (Federal/Estadual/ICMS/PIS/COFINS)
        ↓
Resposta (CupomLeituraDTO): estabelecimento, data, itens, fonte, baixaConfianca
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
| `OcrService` | OCR do texto da imagem (binário `tesseract` via ProcessBuilder); prepara a imagem (tons de cinza, contraste, ampliação) e escolhe entre `--psm 6`/`--psm 4` o melhor texto |
| `CupomParser` | Interpreta o texto do OCR → itens/estabelecimento/data; ignora rodapé/tributos |
| `CupomLeituraDTO` / `ItemCupomLeituraDTO` | Prévia (resposta da API) |

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
- O parser cobre formatos brasileiros comuns (nome + quantidade + preço BR).
