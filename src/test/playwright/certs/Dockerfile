# Build the latest version of mkcert and provide it inside an alpine image

FROM golang:alpine as builder
RUN apk add git
WORKDIR /go/mkcert
RUN git clone https://github.com/FiloSottile/mkcert . && go build -ldflags "-X main.Version=$(git describe --tags)"

FROM alpine:3
COPY --from=builder /go/mkcert/mkcert /usr/local/bin/mkcert
RUN apk add nss && mkdir /certs
WORKDIR /certs
VOLUME /certs
