# 第 8 周：node 构建静态产物 + nginx 托管（history 路由回落 + /api 反代，见 nginx.conf）
FROM node:22-alpine AS build
# 受限网络可选（.env，默认空 = 直连官方源）：
#   BUILD_PROXY_URL=http://host.docker.internal:7897  依赖下载走本机代理
#   NPM_REGISTRY=https://registry.npmmirror.com  换 npm 源
ARG BUILD_PROXY_URL=
ARG NPM_REGISTRY=
WORKDIR /build
# 先只拷依赖清单，利用 docker layer 缓存：依赖未变时跳过 npm ci
COPY package*.json ./
RUN if [ -n "$BUILD_PROXY_URL" ]; then npm config set proxy "$BUILD_PROXY_URL" && npm config set https-proxy "$BUILD_PROXY_URL"; fi; \
    if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi; \
    npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
