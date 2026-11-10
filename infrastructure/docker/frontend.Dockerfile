# ==============================================================================
# Multi-stage Dockerfile for Angular Frontend
# ==============================================================================

# Stage 1: Build Angular Static Artifacts
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --prefer-offline
COPY . .
RUN npm run build -- --configuration=production

# Stage 2: Serve via Nginx
FROM nginx:1.27-alpine
COPY --from=build /app/dist/islandride-frontend/browser /usr/share/nginx/html
COPY ./infrastructure/nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
