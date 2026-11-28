# Build the latest version of mkcert and provide it inside an alpine image

FROM golang:alpine@sha256:70bd7ddea8b6cbe5d6943cde279d6b67feb10cc924fb9100d1d8757c1575b280 as builder
RUN apk add git
WORKDIR /go/mkcert
RUN git clone https://github.com/FiloSottile/mkcert . && go build -ldflags "-X main.Version=$(git describe --tags)"

FROM alpine:3@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf
COPY --from=builder /go/mkcert/mkcert /usr/local/bin/mkcert
RUN apk add nss && mkdir /certs
WORKDIR /certs
VOLUME /certs
