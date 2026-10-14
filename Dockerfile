# ==============================================================================
# InstantDRS Complete Production Multi-Stage Dockerfile
# Java 21 LTS + Python 3 + FFmpeg + OpenVINO Video Review Engine
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Spring Boot Executable JAR
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Resolve Java dependencies offline
RUN ./mvnw dependency:go-offline -B

# Copy backend source code
COPY src ./src

# Package production Spring Boot JAR (skipping unit tests during image build)
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Production Runtime with Video Engine & FFmpeg
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="InstantDRS Team"
LABEL description="InstantDRS Backend Orchestrator + Python Fast Video Review Engine"

# Prevent interactive prompts during apt installation
ENV DEBIAN_FRONTEND=noninteractive

# Install system dependencies: Python 3, FFmpeg, OpenCV runtime libraries, curl
# Note: libgl1-mesa-glx was split/renamed to libgl1 in Ubuntu 22.04 (Jammy)
# Note: -o Acquire::Retries=3 retries on transient Railway network drops
RUN apt-get update -o Acquire::Retries=3 && \
    apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        python3-venv \
        ffmpeg \
        libgl1 \
        libglib2.0-0 \
        ca-certificates \
        curl && \
    rm -rf /var/lib/apt/lists/*

# Set up an isolated Python virtual environment to avoid PEP 668 conflicts
ENV VIRTUAL_ENV=/opt/venv
RUN python3 -m venv $VIRTUAL_ENV
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

# Install exact Python machine learning & video processing dependencies
# requirements.txt is copied for reference; versions are pinned explicitly below
# to keep the Dockerfile self-contained and enable independent Railway layer caching.
COPY video-engine/requirements.txt /tmp/video-engine-requirements.txt

# Step A: Upgrade pip tooling
RUN pip install --no-cache-dir --timeout 600 --upgrade pip setuptools wheel

# Step B: Install PyTorch CPU stack first (~800 MB from download.pytorch.org)
# Separate layer so Railway caches it independently — avoids re-downloading on every rebuild
RUN pip install --no-cache-dir --timeout 600 \
        --extra-index-url https://download.pytorch.org/whl/cpu \
        torch==2.1.0+cpu \
        torchvision==0.16.0+cpu

# Step C: Install remaining ML/video-processing dependencies
# numpy is bounded to <2.0.0 because torch 2.1.0 and ultralytics 8.2.0
# were compiled against NumPy 1.x ABI and will fail at import with NumPy 2.x
RUN pip install --no-cache-dir --timeout 600 \
        ultralytics==8.2.0 \
        openvino==2024.1.0 \
        "opencv-python-headless>=4.8.0" \
        "numpy>=1.24.0,<2.0.0" \
        "polars>=0.20.0" \
        "psutil>=5.9.0" && \
    rm /tmp/video-engine-requirements.txt

# Create application user and group for non-root execution
RUN groupadd -r instantdrs && useradd -r -g instantdrs -m -d /app instantdrs

WORKDIR /app

# Provision storage directories for uploaded and processed video files
RUN mkdir -p /app/storage/original /app/storage/processed /app/video-engine && \
    chmod -R 777 /app/storage && \
    chown -R instantdrs:instantdrs /app $VIRTUAL_ENV

# Copy compiled Spring Boot executable JAR from builder stage
COPY --from=builder --chown=instantdrs:instantdrs /build/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

# Copy complete Python Video Engine, OpenVINO FP32 models, and YOLOv8 weights
COPY --chown=instantdrs:instantdrs video-engine /app/video-engine
RUN cp /app/video-engine/tool3-sports-ball-tracking/yolov8n.pt /app/yolov8n.pt && \
    chown instantdrs:instantdrs /app/yolov8n.pt

# Switch to unprivileged system user
USER instantdrs

# Production environment variable configuration
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    STORAGE_ORIGINAL_DIR=/app/storage/original/ \
    STORAGE_PROCESSED_DIR=/app/storage/processed/ \
    VIDEO_ENGINE_PYTHON=/opt/venv/bin/python \
    VIDEO_ENGINE_PIPELINE=/app/video-engine/pipeline/run_pipeline.py \
    VIDEO_ENGINE_FAST_PIPELINE=/app/video-engine/pipeline/fast_drs_pipeline.py \
    VIDEO_ENGINE_USE_FAST=true

# Expose HTTP port
EXPOSE 8080

# Container healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:${PORT}/api/health || exit 1

# Launch Spring Boot Application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
