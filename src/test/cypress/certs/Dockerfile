# Build the latest version of mkcert and provide it inside an alpine image

FROM golang:alpine as builder
RUN apk add git
RUN git clone https://github.com/FiloSottile/mkcert && cd /go/mkcert && go build -ldflags "-X main.Version=$(git describe --tags)"

FROM alpine
COPY --from=builder /go/mkcert/mkcert /usr/local/bin/mkcert
RUN apk add nss && mkdir /certs
WORKDIR /certs
VOLUME /certs
