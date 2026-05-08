FROM python:3.12-slim

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
    build-essential \
    libc6-dev \
    libseccomp-dev \
    pkg-config \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY workers/dynamic/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY agent-dynamic-c /opt/agent-dynamic-c
RUN make -C /opt/agent-dynamic-c

COPY workers/dynamic/src /app/src

ENV PYTHONPATH=/app/src
ENV DYNAMIC_AGENT_PATH=/opt/agent-dynamic-c/agent_dynamic

CMD ["python", "-m", "worker_dynamic.cli.main"]
