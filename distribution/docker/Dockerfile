#
# Copyright (C) 2024 BEARTELL
# Copyright (C) 2017 Dremio Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
ARG JAVA_IMAGE="eclipse-temurin:11-jdk"
FROM ${JAVA_IMAGE} as base

LABEL maintainer=Beartell

ARG DOWNLOAD_URL

RUN \
  apt-get update \
  && apt-get install wget -y \
  && rm -rf /var/lib/apt/lists/* \
  \
  && mkdir -p /opt/iglo \
  && mkdir -p /var/lib/iglo \
  && mkdir -p /var/run/iglo \
  && mkdir -p /var/log/iglo \
  && mkdir -p /opt/iglo/data \
  \
  && groupadd --system iglo \
  && useradd --base-dir /var/lib/iglo --system --gid iglo iglo \
  && chown -R iglo:iglo /opt/iglo/data \
  && chown -R iglo:iglo /var/run/iglo \
  && chown -R iglo:iglo /var/log/iglo \
  && chown -R iglo:iglo /var/lib/iglo \
  && wget -q "${DOWNLOAD_URL}" -O iglo.tar.gz \
  && tar vxfz iglo.tar.gz -C /opt/iglo --strip-components=1 \
  && rm -rf iglo.tar.gz

EXPOSE 9047/tcp
EXPOSE 31010/tcp
EXPOSE 32010/tcp
EXPOSE 45678/tcp

USER iglo
WORKDIR /opt/iglo
ENV IGLO_HOME /opt/iglo
ENV IGLO_PID_DIR /var/run/iglo
ENV IGLO_GC_LOGS_ENABLED="yes"
ENV IGLO_GC_LOG_TO_CONSOLE="yes"
ENV IGLO_LOG_DIR="/var/log/iglo"
ENTRYPOINT ["bin/iglo", "start-fg"]
