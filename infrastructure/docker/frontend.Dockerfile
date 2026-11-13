# ==============================================================================
# Multi-stage Dockerfile for Angular Frontend
# Build context: Repository Root (.)
# ==============================================================================

# Stage 1: Build Angular Static Artifacts
FROM node:24-alpine AS build
WORKDIR /app

# Copy package manifests from frontend directory
COPY frontend/package*.json ./

# Install dependencies using the existing lockfile
RUN npm ci --prefer-offline

# Copy Angular application source from frontend directory
COPY frontend/ ./

# Build production artifacts
RUN npm run build -- --configuration=production

# Stage 2: Serve via Nginx
FROM nginx:1.27-alpine

# Copy built Angular application into Nginx html serving directory
COPY --from=build /app/dist/islandride-frontend/browser /usr/share/nginx/html

# Copy edge Nginx reverse proxy configuration
COPY infrastructure/nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

