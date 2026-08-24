# 第 8 周：多阶段构建——maven 打包 + JRE 运行，最终镜像只含产物 jar
# 注意：.dockerignore 已排除 application-local.yml（真实 DeepSeek Key 不进镜像，
# 容器内通过 compose 环境变量 DEEPSEEK_API_KEY 注入）
FROM maven:3.9-eclipse-temurin-17 AS build
# 受限网络可选（.env，默认空 = 直连官方源）：
#   BUILD_PROXY_URL=http://host.docker.internal:7897  依赖下载走本机代理
#   MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public  换镜像源（两者都配时代理优先）
ARG BUILD_PROXY_URL=
ARG MAVEN_MIRROR_URL=
RUN mkdir -p /root/.m2 && { \
      PROXY_XML=''; MIRROR_XML=''; \
      if [ -n "$BUILD_PROXY_URL" ]; then \
        HOST_PORT=$(printf '%s' "$BUILD_PROXY_URL" | sed -E 's|^https?://||'); \
        PROXY_XML=$(printf '<proxies><proxy><id>local</id><active>true</active><protocol>http</protocol><host>%s</host><port>%s</port></proxy></proxies>' "${HOST_PORT%:*}" "${HOST_PORT##*:}"); \
      fi; \
      if [ -n "$MAVEN_MIRROR_URL" ]; then \
        MIRROR_XML=$(printf '<mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors>' "$MAVEN_MIRROR_URL"); \
      fi; \
      printf '<settings>%s%s</settings>' "$PROXY_XML" "$MIRROR_XML"; \
    } > /root/.m2/settings.xml
WORKDIR /build
COPY . .
# 坑：本机仓库 backend/.mvn/maven.config 写死 Windows 本地仓库路径（D:\mvn-repository），
# 容器内不删会导致 maven 启动失败（tracked 文件，只删工作区副本不影响仓库）
RUN rm -f .mvn/maven.config && mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/smartfactory-mes-backend-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
