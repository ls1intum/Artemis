# Build the latest version of mkcert and provide it inside an alpine image

FROM golang:alpine@sha256:8a5910f31396cd4d89662f56c68b3ae31d374308270a1c3bd96672ee5ed43414 as builder
RUN apk add git
WORKDIR /go/mkcert
RUN git clone https://github.com/FiloSottile/mkcert . && go build -ldflags "-X main.Version=$(git describe --tags)"

FROM alpine:3@sha256:294b683cb724975bec92580e1e685676bd4b50bda910ddb8c51d4cabeaec77e6
COPY --from=builder /go/mkcert/mkcert /usr/local/bin/mkcert
RUN apk add nss && mkdir /certs
WORKDIR /certs
VOLUME /certs
