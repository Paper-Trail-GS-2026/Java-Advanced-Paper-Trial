# Avaliação da orientação com RAG

> Todo o corpus e os casos são **FICTÍCIOS** (serviços 1, 2 e 3 do seed, URLs `example.invalid`). Cidade e serviços reais ainda são pendência do grupo. Nada aqui representa exigência de órgão público.

## Arquivos versionados
| Arquivo | Conteúdo |
|---|---|
| `src/test/resources/guidance/corpus-ficticio.json` | 10 trechos fictícios (inclui um desatualizado, versão 1, e um com instrução maliciosa) |
| `src/test/resources/guidance/evaluation-dataset.json` | 22 casos em 7 categorias |

Categorias: respondível (6), paráfrase (4), sem fonte (4), serviço errado (3), instrução maliciosa (2), conflitante/desatualizada (2), modelo fora do ar (1).
O esperado é: trechos recuperados, referências corretas, abstenção, HTTP e termos proibidos/obrigatórios. **Não** se exige igualdade literal da resposta.

## Como executar
Avaliação offline (sem IA, sem chave, sem banco):
```
gradlew.bat test --tests "*RetrievalEvaluationTest"
```
Relatório: `build/reports/guidance-evaluation-offline.md`.

Avaliação real com a OpenAI (opt-in; só roda se `OPENAI_API_KEY` existir; só o banco é trocado por memória):
```powershell
$env:OPENAI_API_KEY = "<sua chave>"; gradlew.bat test --tests "*LiveEvaluationTest"
```
Relatório: `build/reports/guidance-evaluation-live.md` (resultado, latência e resposta truncada por caso).

## Resultados reais
### Offline (09/11/2026, dublê de teste no lugar do modelo)
- 22 casos: **21 acertos, 1 falha conhecida (P1)**.
- Acertos: recuperação dos trechos esperados nos casos respondíveis, em 3 das 4 paráfrases, abstenção nos 7 casos sem fonte/serviço errado, trecho desatualizado (v1) nunca recuperado, 503 quando o modelo falha.
- **P1 (falha conhecida):** "Que papel com foto devo apresentar para me cadastrar?" compartilha só a palavra "foto" com o trecho; a busca lexical não recupera e a aplicação se abstém. Embeddings ou sinônimos resolveriam; não foram adotados (proposta lexical aprovada, a confirmar com o professor).
- **Fragilidade:** N1 ("Qual o valor da taxa do cadastro?") e N4 ("Posso fazer o cadastro pela internet?") só se abstêm porque 1 de 3 termos (0,33) fica logo abaixo do limiar de 0,34. Uma pergunta sem fonte que cite um termo comum do serviço pode passar o limiar; nesse caso o modelo é instruído a apontar a lacuna, mas isso não foi medido.
- Limitação: o dataset é pequeno e foi escrito pelo autor do recuperador; os números não generalizam.

### Real com a OpenAI
**Ainda não há resultado.** Em 09/11/2026 todas as chamadas ao modelo voltaram HTTP 401 (chave rejeitada pela OpenAI), então correção factual, referências na resposta gerada, resistência à injeção e latência do modelo **não foram medidas**. Os casos de abstenção (7) passam sem chamar o modelo, mas isso não comprova a IA. Este arquivo será atualizado quando houver uma chave válida.
