COMPOSE_FILE=infra/compose/docker-compose.yml
COMPOSE_PROD_FILE=infra/compose/docker-compose.prod.yml

.PHONY: up up-prod down down-prod restart restart-prod test smoke

up:
	docker compose -f $(COMPOSE_FILE) up -d --build

up-prod:
	docker compose -f $(COMPOSE_FILE) -f $(COMPOSE_PROD_FILE) up -d --build

down:
	docker compose -f $(COMPOSE_FILE) down

down-prod:
	docker compose -f $(COMPOSE_FILE) -f $(COMPOSE_PROD_FILE) down

restart: down up

restart-prod: down-prod up-prod

test:
	cd backend && mvn test
	cd frontend && npm run build
	cd workers/static && python3 -m venv .venv && .venv/bin/pip install -q -r requirements.txt -r requirements-dev.txt && PYTHONPATH=src .venv/bin/python -m pytest -q
	cd workers/dynamic && python3 -m venv .venv && .venv/bin/pip install -q -r requirements.txt -r requirements-dev.txt && PYTHONPATH=src .venv/bin/python -m pytest -q
	cd agent-dynamic-c && make

smoke:
	bash tools/smoke_e2e.sh
