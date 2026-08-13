FROM node:22-bookworm-slim

WORKDIR /app/website

COPY website/package.json website/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY website/ ./
RUN npm run railway:build

ENV NODE_ENV=production
EXPOSE 3000

CMD ["npm", "run", "railway:start"]
