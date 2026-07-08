# GM Autônomo (automação por cron)

O Game Master pode rodar **sozinho** em intervalos, tirando snapshot, checando
tendências/feedback e criando eventos / ajustando drops/NPCs conforme um prompt
reativo. **Toda a infraestrutura já existe** — abaixo está como ligar, ajustar e
pausar. Por padrão, **no ambiente local isto NÃO está ligado** (falta um poller).

> ⚠️ Cada ciclo autônomo é uma sessão do Claude (~5–13 min, gasta cota da sua
> assinatura) e o GM **altera o mundo do jogo sem aprovação por ação**. Ligue
> só quando quiser de fato um servidor "vivo" se auto-gerindo.

## Componentes (já prontos)

| Peça | O quê |
|---|---|
| Tabela `gm_schedule` (id=1) | Config: `enabled`, `interval_hours`, `prompt`, `model`, `last_run`, `next_run` |
| `POST /api/gm/cron` | Roda **um** ciclo autônomo: auto-expira eventos, circuit breaker (3 erros seguidos = para), monta o prompt reativo (`buildCronPrompt`), chama `runGameMaster`. Protegido por `x-gm-secret`. |
| `GET /api/gm/cron/check` | **Público.** Lê o `gm_schedule`; se `enabled` e `next_run` já passou, dispara o `/api/gm/cron`. Tem dedup/self-healing. É o que um cron externo deve chamar a cada 5–10 min. |
| Produção (Fly.io) | Um **GitHub Actions** (`.github/workflows/gm-cron.yml`) bate no `/api/gm/cron/check` periodicamente. |

## Como LIGAR localmente

Falta só um poller batendo no `/api/gm/cron/check`. Duas formas:

### Opção A — serviço poller no compose (persistente)
Adicione ao `docker-compose.local.yml` (dentro de `services:`), e suba com
`docker compose -f docker-compose.local.yml up -d gm-cron`:

```yaml
  gm-cron:
    image: alpine:3.20
    command: sh -c "while true; do wget -q -O- http://dashboard:3000/api/gm/cron/check >/dev/null 2>&1 || true; sleep 300; done"
    depends_on:
      - dashboard
    restart: unless-stopped
```

### Opção B — disparo manual (on-demand, sem loop)
Rodar UM ciclo agora, sem deixar nada ligado:

```bash
# de dentro do container do dashboard (usa o x-gm-secret do env):
docker compose -f docker-compose.local.yml exec -T dashboard \
  node -e "fetch('http://localhost:3000/api/gm/cron',{method:'POST',headers:{'Content-Type':'application/json','x-gm-secret':process.env.GM_API_SECRET},body:'{\"trigger\":\"manual\"}'}).then(r=>r.text()).then(console.log)"
```

## Como CONFIGURAR

Ajuste a linha `id=1` de `gm_schedule` (via SQL ou pela tela `/gamemaster` do dashboard):

```sql
-- ligar/desligar
UPDATE gm_schedule SET enabled = 1 WHERE id = 1;   -- 0 = pausa
-- intervalo (horas, inteiro; mínimo 1)
UPDATE gm_schedule SET interval_hours = 6 WHERE id = 1;
-- prompt do ciclo (deixe NULL para usar o buildCronPrompt reativo padrão)
UPDATE gm_schedule SET prompt = 'Review server health and make adjustments as needed.' WHERE id = 1;
-- modelo (tem que ser um claude-*)
UPDATE gm_schedule SET model = 'claude-haiku-4-5' WHERE id = 1;
-- forçar rodar no próximo tick (next_run no passado)
UPDATE gm_schedule SET next_run = NOW() WHERE id = 1;
```

## Como PAUSAR
`UPDATE gm_schedule SET enabled = 0 WHERE id = 1;` (ou pare/derrube o serviço `gm-cron`).

## Notas para o ambiente LOCAL
- Sem jogadores online, os gatilhos reativos quase não disparam → o GM tende a
  "observar e não fazer nada" (que o próprio prompt trata como opção válida).
- Ferramentas que dependem de serviços externos (`generate_item`/Flux,
  `create_custom_reactor`/R2, `publish_*`) falham de forma controlada no local;
  o ciclo segue com as ferramentas de DB/config que funcionam.
- Spawns/reactors colocados por evento só aparecem após restart do servidor no
  local (o auto-restart usa a API do Fly).
