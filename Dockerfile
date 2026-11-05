FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production HOST=0.0.0.0 PORT=8080
COPY --chown=node:node distribution-server.cjs ./
COPY --chown=node:node releases ./releases
USER node
EXPOSE 8080
CMD ["node", "distribution-server.cjs"]
